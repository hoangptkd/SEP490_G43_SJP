package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.BankTransferProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CheckoutRequest;
import com.sjp.recruitment.model.dto.response.BankTransferInfo;
import com.sjp.recruitment.model.dto.response.CheckoutResponse;
import com.sjp.recruitment.model.dto.response.PaymentStatusResponse;
import com.sjp.recruitment.model.dto.response.PlanCatalogResponse;
import com.sjp.recruitment.model.dto.response.UserSubscriptionResponse;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.payment.MomoPaymentGateway;
import com.sjp.recruitment.payment.PayOsPaymentGateway;
import com.sjp.recruitment.payment.VnPayPaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final AuthService authService;
    private final PlatformTransactionManager transactionManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final MomoPaymentGateway momoPaymentGateway;
    private final VnPayPaymentGateway vnPayPaymentGateway;
    private final PayOsPaymentGateway payOsPaymentGateway;
    private final BankTransferProperties bankTransferProperties;
    private final FeatureLimitService featureLimitService;
    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public List<PlanCatalogResponse> listAvailablePlans() {
        User user = authService.getCurrentUser();
        String targetRole = resolveTargetRole(user);
        return jdbc.query("""
                        SELECT id::text AS id, name, target_role, description, price, currency,
                               duration_days, COALESCE(features::text, '{}') AS features_json, sort_order
                        FROM plans
                        WHERE status = 'active'
                          AND price > 0
                          AND (target_role = :targetRole OR target_role = 'all')
                        ORDER BY sort_order ASC, price ASC, name ASC
                        """,
                new MapSqlParameterSource("targetRole", targetRole),
                this::mapPlan);
    }

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        User user = authService.getCurrentUser();
        if (user.getRoleEnum() == User.UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_CANNOT_PURCHASE", "Tài khoản quản trị không thể mua gói");
        }
        if (request == null || !StringUtils.hasText(request.planId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_REQUIRED", "Vui lòng chọn gói dịch vụ");
        }

        PlanRow plan = findActivePlan(request.planId().trim(), resolveTargetRole(user));
        String paymentMethod = StringUtils.hasText(request.paymentMethod())
                ? request.paymentMethod().trim().toLowerCase(Locale.ROOT)
                : systemSettingsService.defaultPaymentProvider();
        if (!List.of("momo", "vnpay", "bank_transfer", "payos").contains(paymentMethod)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GATEWAY",
                    "Phương thức thanh toán không hỗ trợ. Chọn payos, momo, vnpay hoặc bank_transfer");
        }

        cancelPendingSubscriptions(user.getId());

        String subscriptionId = createPendingSubscription(user.getId(), plan.id(), plan.durationDays());
        String paymentId = createPendingPayment(user.getId(), subscriptionId, plan.price(), plan.currency(), paymentMethod);

        if (plan.price().compareTo(BigDecimal.ZERO) <= 0) {
            activatePaidSubscription(subscriptionId, plan);
            markPaymentPaid(paymentId, "FREE-" + paymentId, Map.of("type", "free"));
            return CheckoutResponse.paid(paymentId, subscriptionId, "Kích hoạt gói miễn phí thành công");
        }

        if (!systemSettingsService.isPaymentGatewayEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_DISABLED",
                    "Cổng thanh toán đang tắt. Vui lòng liên hệ quản trị viên.");
        }

        long amountVnd = plan.price().setScale(0, RoundingMode.HALF_UP).longValue();
        String orderInfo = "Thanh toan goi " + plan.name();

        return switch (paymentMethod) {
            case "vnpay" -> checkoutVnPay(paymentId, subscriptionId, amountVnd, orderInfo);
            case "bank_transfer" -> checkoutBankTransfer(paymentId, subscriptionId, plan);
            case "momo" -> checkoutMomo(paymentId, subscriptionId, amountVnd, orderInfo);
            default -> checkoutPayOs(paymentId, subscriptionId, amountVnd, orderInfo);
        };
    }

    private CheckoutResponse checkoutPayOs(String paymentId, String subscriptionId, long amountVnd, String orderInfo) {
        var result = payOsPaymentGateway.createPayment(paymentId, amountVnd, orderInfo);
        jdbc.update("""
                        UPDATE payments
                        SET gateway = 'payos',
                            gateway_order_id = :orderId,
                            gateway_response = CAST(:response AS jsonb)
                        WHERE id = CAST(:paymentId AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("orderId", String.valueOf(result.orderCode()))
                        .addValue("response", objectMapper.createObjectNode()
                                .put("orderCode", result.orderCode())
                                .put("paymentLinkId", result.paymentLinkId())
                                .put("qrCode", result.qrCode())
                                .set("createResponse", result.rawResponse())
                                .toString())
                        .addValue("paymentId", paymentId));
        return CheckoutResponse.redirect(paymentId, subscriptionId, "pending", result.checkoutUrl(),
                "Chuyển đến PayOS để thanh toán", "payos");
    }

    private CheckoutResponse checkoutMomo(String paymentId, String subscriptionId, long amountVnd, String orderInfo) {
        var momoResult = momoPaymentGateway.createPayment(paymentId, amountVnd, orderInfo);
        jdbc.update("""
                        UPDATE payments
                        SET gateway = 'momo',
                            gateway_order_id = :orderId,
                            gateway_response = CAST(:response AS jsonb)
                        WHERE id = CAST(:paymentId AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("orderId", paymentId)
                        .addValue("response", objectMapper.createObjectNode()
                                .put("requestId", momoResult.requestId())
                                .set("createResponse", momoResult.rawResponse())
                                .toString())
                        .addValue("paymentId", paymentId));
        return CheckoutResponse.redirect(paymentId, subscriptionId, "pending", momoResult.payUrl(),
                "Chuyển đến MoMo để thanh toán", "momo");
    }

    private CheckoutResponse checkoutVnPay(String paymentId, String subscriptionId, long amountVnd, String orderInfo) {
        String payUrl = vnPayPaymentGateway.createPaymentUrl(paymentId, amountVnd, orderInfo, "127.0.0.1");
        jdbc.update("""
                        UPDATE payments
                        SET gateway = 'vnpay',
                            gateway_order_id = :orderId,
                            gateway_response = CAST(:response AS jsonb)
                        WHERE id = CAST(:paymentId AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("orderId", paymentId.replace("-", ""))
                        .addValue("response", "{\"created\":true}")
                        .addValue("paymentId", paymentId));
        return CheckoutResponse.redirect(paymentId, subscriptionId, "pending", payUrl,
                "Chuyển đến VNPay để thanh toán", "vnpay");
    }

    private CheckoutResponse checkoutBankTransfer(String paymentId, String subscriptionId, PlanRow plan) {
        if (!bankTransferProperties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BANK_TRANSFER_DISABLED", "Chuyển khoản ngân hàng chưa được bật");
        }
        String orderCode = "SJP" + paymentId.replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        String content = "SJP " + paymentId.substring(0, 8).toUpperCase(Locale.ROOT);
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plusMinutes(Math.max(5, bankTransferProperties.getExpireMinutes()));
        long amountVnd = plan.price().setScale(0, RoundingMode.HALF_UP).longValue();
        String qrUrl = buildVietQrUrl(
                bankTransferProperties.getBankCode(),
                bankTransferProperties.getAccountNumber(),
                bankTransferProperties.getAccountName(),
                amountVnd,
                content
        );
        BankTransferInfo info = new BankTransferInfo(
                paymentId,
                orderCode,
                plan.name(),
                bankTransferProperties.getBankName(),
                bankTransferProperties.getBankCode(),
                bankTransferProperties.getAccountNumber(),
                bankTransferProperties.getAccountName(),
                bankTransferProperties.getBranch(),
                content,
                plan.price(),
                plan.currency(),
                qrUrl,
                createdAt,
                expiresAt,
                "pending"
        );
        try {
            jdbc.update("""
                            UPDATE payments
                            SET gateway = 'bank_transfer',
                                gateway_order_id = :orderId,
                                gateway_response = CAST(:response AS jsonb)
                            WHERE id = CAST(:paymentId AS uuid)
                            """,
                    new MapSqlParameterSource()
                            .addValue("orderId", content)
                            .addValue("response", objectMapper.writeValueAsString(info))
                            .addValue("paymentId", paymentId));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BANK_TRANSFER_FAILED", "Không tạo được yêu cầu chuyển khoản");
        }
        return CheckoutResponse.bank(paymentId, subscriptionId,
                "Quét QR hoặc chuyển khoản đúng nội dung. Admin sẽ xác nhận sau khi nhận tiền.", info);
    }

    @Transactional
    public BankTransferInfo getBankTransferCheckout(String paymentId) {
        User user = authService.getCurrentUser();
        ensureUuid(paymentId, "Thanh toán");
        List<BankTransferInfo> rows = jdbc.query("""
                        SELECT p.id::text AS id,
                               p.status,
                               p.amount,
                               p.currency,
                               p.created_at,
                               p.gateway_response::text AS gateway_response,
                               pl.name AS plan_name
                        FROM payments p
                        LEFT JOIN subscriptions s ON s.id = p.subscription_id
                        LEFT JOIN plans pl ON pl.id = s.plan_id
                        WHERE p.id = CAST(:paymentId AS uuid)
                          AND p.user_id = CAST(:userId AS uuid)
                          AND p.payment_method = 'bank_transfer'
                        """,
                new MapSqlParameterSource()
                        .addValue("paymentId", paymentId)
                        .addValue("userId", user.getId().toString()),
                (rs, rowNum) -> parseBankTransferInfo(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("plan_name"),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("gateway_response")
                ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy yêu cầu chuyển khoản");
        }
        BankTransferInfo info = rows.get(0);
        // Hết hạn QR phía user: chỉ đổi trạng thái hiển thị.
        // Giữ status=pending trong DB thêm 1 ngày để admin vẫn xác nhận được nếu tiền đã về.
        if ("pending".equalsIgnoreCase(info.status()) && info.expiresAt() != null && info.expiresAt().isBefore(LocalDateTime.now())) {
            return new BankTransferInfo(
                    info.paymentId(), info.orderCode(), info.planName(), info.bankName(), info.bankCode(),
                    info.accountNumber(), info.accountName(), info.branch(), info.transferContent(),
                    info.amount(), info.currency(), info.qrUrl(), info.createdAt(), info.expiresAt(), "expired"
            );
        }
        return info;
    }

    private BankTransferInfo parseBankTransferInfo(String paymentId, String status, BigDecimal amount, String currency,
                                                   String planName, LocalDateTime createdAt, String gatewayResponse) {
        try {
            if (StringUtils.hasText(gatewayResponse)) {
                BankTransferInfo stored = objectMapper.readValue(gatewayResponse, BankTransferInfo.class);
                return new BankTransferInfo(
                        paymentId,
                        stored.orderCode() != null ? stored.orderCode() : "SJP" + paymentId.substring(0, 8).toUpperCase(Locale.ROOT),
                        planName != null ? planName : stored.planName(),
                        stored.bankName(),
                        stored.bankCode(),
                        stored.accountNumber(),
                        stored.accountName(),
                        stored.branch(),
                        stored.transferContent(),
                        amount != null ? amount : stored.amount(),
                        currency != null ? currency : stored.currency(),
                        stored.qrUrl(),
                        stored.createdAt() != null ? stored.createdAt() : createdAt,
                        stored.expiresAt(),
                        status
                );
            }
        } catch (Exception ignored) {
        }
        return new BankTransferInfo(
                paymentId, "SJP" + paymentId.substring(0, 8).toUpperCase(Locale.ROOT), planName,
                bankTransferProperties.getBankName(), bankTransferProperties.getBankCode(),
                bankTransferProperties.getAccountNumber(), bankTransferProperties.getAccountName(),
                bankTransferProperties.getBranch(), "", amount, currency, null, createdAt, null, status
        );
    }

    private String buildVietQrUrl(String bankCode, String accountNumber, String accountName, long amount, String addInfo) {
        String template = "compact2";
        String base = "https://img.vietqr.io/image/"
                + encode(bankCode) + "-" + encode(accountNumber) + "-" + template + ".png";
        return base
                + "?amount=" + amount
                + "&addInfo=" + encode(addInfo)
                + "&accountName=" + encode(accountName);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public PaymentStatusResponse getPaymentStatus(String paymentId) {
        User user = authService.getCurrentUser();
        ensureUuid(paymentId, "Thanh toán");
        PaymentStatusResponse current = getPaymentStatusForUser(paymentId, user.getId().toString());
        if ("pending".equalsIgnoreCase(current.status())) {
            // Sync trong transaction riêng — lỗi SQL khi cập nhật paid không được làm abort luôn bước đọc status
            syncPendingPaymentIsolated(paymentId);
            return getPaymentStatusForUser(paymentId, user.getId().toString());
        }
        return current;
    }

    @Transactional(readOnly = true)
    public UserSubscriptionResponse getMySubscription() {
        User user = authService.getCurrentUser();
        List<UserSubscriptionResponse> rows = jdbc.query("""
                        SELECT pl.id::text AS plan_id,
                               pl.name AS plan_name,
                               COALESCE(s.status, 'free') AS status,
                               COALESCE(pl.price, 0) AS price,
                               COALESCE(pl.currency, 'VND') AS currency,
                               COALESCE(pl.features::text, '{}') AS features_json,
                               s.start_date,
                               s.end_date
                        FROM users u
                        LEFT JOIN LATERAL (
                            SELECT *
                            FROM subscriptions
                            WHERE user_id = u.id AND status = 'active'
                              AND (end_date IS NULL OR end_date > now())
                            ORDER BY start_date DESC NULLS LAST, created_at DESC
                            LIMIT 1
                        ) s ON true
                        LEFT JOIN plans pl ON pl.id = s.plan_id
                        WHERE u.id = CAST(:userId AS uuid)
                        """,
                new MapSqlParameterSource("userId", user.getId().toString()),
                (rs, rowNum) -> new UserSubscriptionResponse(
                        rs.getString("plan_id"),
                        rs.getString("plan_name") == null ? "Free" : rs.getString("plan_name"),
                        rs.getString("status"),
                        rs.getBigDecimal("price"),
                        rs.getString("currency"),
                        parseBenefits(rs.getString("features_json")),
                        rs.getTimestamp("start_date") == null ? null : rs.getTimestamp("start_date").toLocalDateTime(),
                        rs.getTimestamp("end_date") == null ? null : rs.getTimestamp("end_date").toLocalDateTime(),
                        List.of()
                ));
        var usages = featureLimitService.getUsageSummary(user);
        if (rows.isEmpty()) {
            return new UserSubscriptionResponse(null, "Free", "free", BigDecimal.ZERO, "VND",
                    defaultBenefits(user), null, null, usages);
        }
        UserSubscriptionResponse current = rows.get(0);
        List<String> benefits = current.benefits().isEmpty() ? defaultBenefits(user) : current.benefits();
        return new UserSubscriptionResponse(
                current.planId(),
                current.planName(),
                current.status(),
                current.price(),
                current.currency(),
                benefits,
                current.startedAt(),
                current.expiresAt(),
                usages
        );
    }

    @Transactional
    public void handlePayOsWebhook(Map<String, Object> payload) {
        Long orderCode = payOsPaymentGateway.extractOrderCode(payload);
        saveWebhookEvent("payos", orderCode == null ? null : String.valueOf(orderCode), payload);

        // PayOS gửi payload mẫu khi xác nhận webhook URL — bỏ qua nếu không map được đơn thật
        if (orderCode == null) {
            return;
        }
        if (!payOsPaymentGateway.verifyWebhookSignature(payload)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký PayOS không hợp lệ");
        }

        String paymentId = findPaymentIdByGatewayOrderId(String.valueOf(orderCode));
        if (!StringUtils.hasText(paymentId)) {
            return;
        }
        completeGatewayPayment(
                paymentId,
                payOsPaymentGateway.extractReference(payload),
                payload,
                payOsPaymentGateway.isSuccess(payload),
                "Thanh toán PayOS thất bại"
        );
    }

    @Transactional
    public void handleMomoIpn(Map<String, Object> payload) {
        saveWebhookEvent("momo", stringValue(payload.get("orderId")), payload);
        if (!momoPaymentGateway.verifyIpnSignature(payload)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký MoMo không hợp lệ");
        }
        String orderId = stringValue(payload.get("orderId"));
        if (!StringUtils.hasText(orderId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER", "Thiếu orderId");
        }
        completeGatewayPayment(orderId, stringValue(payload.get("transId")), payload,
                momoPaymentGateway.isSuccessResultCode(payload.get("resultCode")),
                "Thanh toán MoMo thất bại");
    }

    @Transactional
    public boolean handleVnPayIpn(Map<String, String> params) {
        saveWebhookEvent("vnpay", params.get("paymentId"), new HashMap<>(params));
        if (!vnPayPaymentGateway.verifySignature(params)) {
            return false;
        }
        String paymentId = resolveVnPayPaymentId(params, null);
        if (!StringUtils.hasText(paymentId)) {
            return false;
        }
        try {
            completeGatewayPayment(paymentId, params.get("vnp_TransactionNo"), new HashMap<>(params),
                    vnPayPaymentGateway.isSuccess(params), "Thanh toán VNPay thất bại");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Transactional
    public void handleVnPayReturn(Map<String, String> params, String paymentIdHint) {
        if (!vnPayPaymentGateway.verifySignature(params)) {
            return;
        }
        String paymentId = resolveVnPayPaymentId(params, paymentIdHint);
        if (!StringUtils.hasText(paymentId)) {
            return;
        }
        completeGatewayPayment(paymentId, params.get("vnp_TransactionNo"), new HashMap<>(params),
                vnPayPaymentGateway.isSuccess(params), "Thanh toán VNPay thất bại");
    }

    public String buildFrontendPaymentResultUrl(String paymentId) {
        return frontendBaseUrl + "/payment/result?paymentId=" + (paymentId == null ? "" : paymentId);
    }

    @Transactional
    public PaymentStatusResponse confirmBankTransferAsAdmin(String paymentId) {
        ensureUuid(paymentId, "Thanh toán");
        PaymentRow payment = findPaymentById(paymentId);
        if (!"pending".equalsIgnoreCase(payment.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_PENDING", "Giao dịch không ở trạng thái chờ xác nhận");
        }
        SubscriptionRow subscription = findSubscription(payment.subscriptionId());
        PlanRow plan = findPlanById(subscription.planId());
        expireActiveSubscriptions(subscription.userId());
        activatePaidSubscription(subscription.id(), plan);
        markPaymentPaid(paymentId, "BANK-" + paymentId.substring(0, 8), Map.of("confirmedBy", "admin"));
        return getPaymentStatusInternal(paymentId);
    }

    private void completeGatewayPayment(String paymentId, String transactionId, Map<String, ?> payload,
                                        boolean success, String failReason) {
        PaymentRow payment = findPaymentById(paymentId);
        if ("paid".equalsIgnoreCase(payment.status())) {
            return;
        }
        if (success) {
            SubscriptionRow subscription = findSubscription(payment.subscriptionId());
            PlanRow plan = findPlanById(subscription.planId());
            expireActiveSubscriptions(subscription.userId());
            activatePaidSubscription(subscription.id(), plan);
            markPaymentPaid(paymentId, resolveTransactionId(paymentId, transactionId), payload);
        } else {
            markPaymentFailed(paymentId, failReason);
            cancelSubscription(payment.subscriptionId(), failReason);
        }
    }

    /** UNIQUE(transaction_id) — chuỗi rỗng '' cũng bị trùng; NULL thì được phép nhiều. */
    private String resolveTransactionId(String paymentId, String transactionId) {
        if (StringUtils.hasText(transactionId)) {
            return transactionId.trim();
        }
        return paymentId;
    }

    private void activatePaidSubscription(String subscriptionId, PlanRow plan) {
        activateSubscription(subscriptionId, plan.durationDays());
        String featuresJson = jdbc.query("""
                        SELECT COALESCE(features::text, '{}') FROM plans WHERE id = CAST(:id AS uuid)
                        """,
                new MapSqlParameterSource("id", plan.id()),
                rs -> rs.next() ? rs.getString(1) : "{}");
        featureLimitService.initUsagesForSubscription(subscriptionId, featuresJson == null ? "{}" : featuresJson);
    }

    private String findPaymentIdByGatewayOrderId(String gatewayOrderId) {
        if (!StringUtils.hasText(gatewayOrderId)) {
            return null;
        }
        List<String> ids = jdbc.query("""
                        SELECT id::text
                        FROM payments
                        WHERE gateway_order_id = :orderId
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource("orderId", gatewayOrderId),
                (rs, rowNum) -> rs.getString(1));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String resolveVnPayPaymentId(Map<String, String> params, String hint) {
        if (StringUtils.hasText(hint)) {
            return hint;
        }
        String txnRef = params.get("vnp_TxnRef");
        if (!StringUtils.hasText(txnRef)) {
            return null;
        }
        List<String> ids = jdbc.query("""
                        SELECT id::text FROM payments
                        WHERE REPLACE(id::text, '-', '') = :txnRef
                           OR gateway_order_id = :txnRef
                        LIMIT 1
                        """,
                new MapSqlParameterSource("txnRef", txnRef),
                (rs, rowNum) -> rs.getString(1));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void syncPendingPayment(String paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT gateway, gateway_order_id, gateway_response::text AS gateway_response
                        FROM payments
                        WHERE id = CAST(:id AS uuid) AND status = 'pending'
                        """,
                new MapSqlParameterSource("id", paymentId));
        if (rows.isEmpty()) {
            return;
        }
        String gateway = stringValue(rows.get(0).get("gateway"));
        if ("payos".equalsIgnoreCase(gateway)) {
            syncPendingPayOs(paymentId, stringValue(rows.get(0).get("gateway_order_id")),
                    stringValue(rows.get(0).get("gateway_response")));
            return;
        }
        if (!"momo".equalsIgnoreCase(gateway)) {
            return;
        }
        String orderId = stringValue(rows.get(0).get("gateway_order_id"));
        if (!StringUtils.hasText(orderId)) {
            orderId = paymentId;
        }
        String requestId = paymentId;
        try {
            String gatewayResponse = stringValue(rows.get(0).get("gateway_response"));
            if (StringUtils.hasText(gatewayResponse)) {
                var node = objectMapper.readTree(gatewayResponse);
                if (node.hasNonNull("requestId")) {
                    requestId = node.get("requestId").asText();
                }
            }
            var response = momoPaymentGateway.queryPayment(orderId, requestId);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(response, Map.class);
            if (momoPaymentGateway.isSuccessResultCode(payload.get("resultCode"))) {
                handleMomoIpn(payload);
            }
        } catch (Exception ignored) {
            // keep pending until IPN arrives
        }
    }

    private void syncPendingPayOs(String paymentId, String orderCode, String gatewayResponseJson) {
        try {
            String queryId = orderCode;
            if (StringUtils.hasText(gatewayResponseJson)) {
                var node = objectMapper.readTree(gatewayResponseJson);
                if (node.hasNonNull("paymentLinkId") && StringUtils.hasText(node.get("paymentLinkId").asText())) {
                    queryId = node.get("paymentLinkId").asText();
                }
            }
            if (!StringUtils.hasText(queryId)) {
                return;
            }
            var response = payOsPaymentGateway.queryPayment(queryId);
            if (!payOsPaymentGateway.isPaidStatus(response)) {
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("code", response.path("code").asText("00"));
            payload.put("success", true);
            payload.put("desc", response.path("desc").asText("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(response.path("data"), Map.class);
            if (data == null) {
                data = new HashMap<>();
            }
            data.putIfAbsent("orderCode", orderCode);
            data.putIfAbsent("code", "00");
            payload.put("data", data);
            String reference = firstNonBlank(
                    stringValue(data.get("reference")),
                    stringValue(data.get("id")),
                    stringValue(data.get("paymentLinkId")),
                    orderCode,
                    paymentId
            );
            completeGatewayPayment(
                    paymentId,
                    reference,
                    payload,
                    true,
                    "Thanh toán PayOS thất bại"
            );
        } catch (Exception ignored) {
            // keep pending until webhook arrives
        }
    }

    private void syncPendingPaymentIsolated(String paymentId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> syncPendingPayment(paymentId));
        } catch (Exception ignored) {
            // Giữ pending; lần poll sau sẽ thử lại
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void saveWebhookEvent(String gateway, String paymentId, Map<?, ?> payload) {
        try {
            jdbc.update("""
                            INSERT INTO payment_webhook_events (payment_id, gateway, event_type, payload_json, status, processed_at)
                            VALUES (
                                CASE WHEN :paymentId ~* '^[0-9a-f-]{36}$' THEN CAST(:paymentId AS uuid) ELSE NULL END,
                                :gateway,
                                'ipn',
                                CAST(:payload AS jsonb),
                                'received',
                                now()
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("paymentId", paymentId == null ? "" : paymentId)
                            .addValue("gateway", gateway)
                            .addValue("payload", objectMapper.writeValueAsString(payload)));
        } catch (Exception ignored) {
            // webhook logging should not block payment processing
        }
    }

    private PaymentStatusResponse getPaymentStatusForUser(String paymentId, String userId) {
        List<PaymentStatusResponse> rows = jdbc.query("""
                        SELECT p.id::text AS id,
                               p.status,
                               p.amount,
                               p.currency,
                               p.payment_method,
                               pl.name AS plan_name,
                               s.status AS subscription_status,
                               p.paid_at,
                               p.failure_reason,
                               p.created_at
                        FROM payments p
                        LEFT JOIN subscriptions s ON s.id = p.subscription_id
                        LEFT JOIN plans pl ON pl.id = s.plan_id
                        WHERE p.id = CAST(:paymentId AS uuid)
                          AND p.user_id = CAST(:userId AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("paymentId", paymentId)
                        .addValue("userId", userId),
                (rs, rowNum) -> new PaymentStatusResponse(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("payment_method"),
                        rs.getString("plan_name"),
                        rs.getString("subscription_status"),
                        rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toLocalDateTime(),
                        rs.getString("failure_reason"),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()
                ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch");
        }
        return rows.get(0);
    }

    private PaymentStatusResponse getPaymentStatusInternal(String paymentId) {
        List<PaymentStatusResponse> rows = jdbc.query("""
                        SELECT p.id::text AS id,
                               p.status,
                               p.amount,
                               p.currency,
                               p.payment_method,
                               pl.name AS plan_name,
                               s.status AS subscription_status,
                               p.paid_at,
                               p.failure_reason,
                               p.created_at
                        FROM payments p
                        LEFT JOIN subscriptions s ON s.id = p.subscription_id
                        LEFT JOIN plans pl ON pl.id = s.plan_id
                        WHERE p.id = CAST(:paymentId AS uuid)
                        """,
                new MapSqlParameterSource("paymentId", paymentId),
                (rs, rowNum) -> new PaymentStatusResponse(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("payment_method"),
                        rs.getString("plan_name"),
                        rs.getString("subscription_status"),
                        rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toLocalDateTime(),
                        rs.getString("failure_reason"),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()
                ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch");
        }
        return rows.get(0);
    }

    private void cancelPendingSubscriptions(UUID userId) {
        // Hủy luôn payment pending của các đơn cũ — không giữ trong "Chờ xác nhận" admin
        jdbc.update("""
                        UPDATE payments p
                        SET status = 'cancelled',
                            failure_reason = 'Thay thế bởi đơn thanh toán mới'
                        WHERE p.status = 'pending'
                          AND EXISTS (
                            SELECT 1 FROM subscriptions s
                            WHERE s.id = p.subscription_id
                              AND s.user_id = CAST(:userId AS uuid)
                              AND s.status = 'pending'
                          )
                        """,
                new MapSqlParameterSource("userId", userId.toString()));
        jdbc.update("""
                        UPDATE subscriptions
                        SET status = 'cancelled',
                            cancelled_at = now(),
                            cancelled_reason = 'Thay thế bởi đơn thanh toán mới',
                            updated_at = now()
                        WHERE user_id = CAST(:userId AS uuid)
                          AND status = 'pending'
                        """,
                new MapSqlParameterSource("userId", userId.toString()));
    }

    private void expireActiveSubscriptions(UUID userId) {
        jdbc.update("""
                        UPDATE subscriptions
                        SET status = 'expired',
                            updated_at = now()
                        WHERE user_id = CAST(:userId AS uuid)
                          AND status = 'active'
                        """,
                new MapSqlParameterSource("userId", userId.toString()));
    }

    private String createPendingSubscription(UUID userId, String planId, int durationDays) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                        INSERT INTO subscriptions (id, user_id, plan_id, status, created_at, updated_at)
                        VALUES (CAST(:id AS uuid), CAST(:userId AS uuid), CAST(:planId AS uuid), 'pending', now(), now())
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId.toString())
                        .addValue("planId", planId));
        return id;
    }

    private void activateSubscription(String subscriptionId, int durationDays) {
        jdbc.update("""
                        UPDATE subscriptions
                        SET status = 'active',
                            start_date = now(),
                            end_date = now() + make_interval(days => :durationDays),
                            updated_at = now()
                        WHERE id = CAST(:id AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", subscriptionId)
                        .addValue("durationDays", durationDays));
    }

    private void cancelSubscription(String subscriptionId, String reason) {
        jdbc.update("""
                        UPDATE subscriptions
                        SET status = 'cancelled',
                            cancelled_at = now(),
                            cancelled_reason = :reason,
                            updated_at = now()
                        WHERE id = CAST(:id AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", subscriptionId)
                        .addValue("reason", reason));
    }

    private String createPendingPayment(UUID userId, String subscriptionId, BigDecimal amount, String currency, String method) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                        INSERT INTO payments (id, subscription_id, user_id, amount, currency, payment_method, status, created_at)
                        VALUES (CAST(:id AS uuid), CAST(:subscriptionId AS uuid), CAST(:userId AS uuid),
                                :amount, :currency, :method, 'pending', now())
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("subscriptionId", subscriptionId)
                        .addValue("userId", userId.toString())
                        .addValue("amount", amount)
                        .addValue("currency", currency)
                        .addValue("method", method));
        return id;
    }

    private void markPaymentPaid(String paymentId, String transactionId, Map<?, ?> gatewayResponse) {
        try {
            Map<String, Object> merged = new HashMap<>();
            List<String> existing = jdbc.query("""
                            SELECT gateway_response::text
                            FROM payments
                            WHERE id = CAST(:paymentId AS uuid)
                            """,
                    new MapSqlParameterSource("paymentId", paymentId),
                    (rs, rowNum) -> rs.getString(1));
            if (!existing.isEmpty() && StringUtils.hasText(existing.get(0))) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> previous = objectMapper.readValue(existing.get(0), Map.class);
                    if (previous != null) {
                        merged.putAll(previous);
                    }
                } catch (Exception ignored) {
                }
            }
            if (gatewayResponse != null) {
                gatewayResponse.forEach((key, value) -> {
                    if (key != null) {
                        merged.put(String.valueOf(key), value);
                    }
                });
            }
            // Giữ ND CK / QR để đối chiếu sau khi thanh toán thành công
            String resolvedTxn = resolveTransactionId(paymentId, transactionId);
            jdbc.update("""
                            UPDATE payments
                            SET status = 'paid',
                                transaction_id = :transactionId,
                                paid_at = now(),
                                gateway_response = CAST(:response AS jsonb)
                            WHERE id = CAST(:paymentId AS uuid)
                            """,
                    new MapSqlParameterSource()
                            .addValue("paymentId", paymentId)
                            .addValue("transactionId", resolvedTxn)
                            .addValue("response", objectMapper.writeValueAsString(merged)));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_UPDATE_FAILED",
                    "Không cập nhật được thanh toán: " + (ex.getMessage() == null ? "unknown" : ex.getMessage()));
        }
    }

    private void markPaymentFailed(String paymentId, String reason) {
        jdbc.update("""
                        UPDATE payments
                        SET status = 'failed',
                            failure_reason = :reason
                        WHERE id = CAST(:paymentId AS uuid)
                        """,
                new MapSqlParameterSource()
                        .addValue("paymentId", paymentId)
                        .addValue("reason", reason));
    }

    private PlanRow findActivePlan(String planId, String targetRole) {
        ensureUuid(planId, "Gói dịch vụ");
        List<PlanRow> rows = jdbc.query("""
                        SELECT id::text AS id, name, target_role, price, currency, duration_days
                        FROM plans
                        WHERE id = CAST(:id AS uuid)
                          AND status = 'active'
                          AND (target_role = :targetRole OR target_role = 'all')
                        """,
                new MapSqlParameterSource()
                        .addValue("id", planId)
                        .addValue("targetRole", targetRole),
                (rs, rowNum) -> mapPlanRow(rs));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Gói dịch vụ không tồn tại hoặc không áp dụng cho tài khoản của bạn");
        }
        return rows.get(0);
    }

    private PlanRow findPlanById(String planId) {
        List<PlanRow> rows = jdbc.query("""
                        SELECT id::text AS id, name, target_role, price, currency, duration_days
                        FROM plans
                        WHERE id = CAST(:id AS uuid)
                        """,
                new MapSqlParameterSource("id", planId),
                (rs, rowNum) -> mapPlanRow(rs));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Không tìm thấy gói dịch vụ");
        }
        return rows.get(0);
    }

    private PaymentRow findPaymentById(String paymentId) {
        List<PaymentRow> rows = jdbc.query("""
                        SELECT id::text AS id, subscription_id::text AS subscription_id, status
                        FROM payments
                        WHERE id = CAST(:id AS uuid)
                        """,
                new MapSqlParameterSource("id", paymentId),
                (rs, rowNum) -> new PaymentRow(
                        rs.getString("id"),
                        rs.getString("subscription_id"),
                        rs.getString("status")
                ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch thanh toán");
        }
        return rows.get(0);
    }

    private SubscriptionRow findSubscription(String subscriptionId) {
        List<SubscriptionRow> rows = jdbc.query("""
                        SELECT id::text AS id, user_id::text AS user_id, plan_id::text AS plan_id, status
                        FROM subscriptions
                        WHERE id = CAST(:id AS uuid)
                        """,
                new MapSqlParameterSource("id", subscriptionId),
                (rs, rowNum) -> new SubscriptionRow(
                        rs.getString("id"),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("plan_id"),
                        rs.getString("status")
                ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Không tìm thấy đăng ký");
        }
        return rows.get(0);
    }

    private PlanCatalogResponse mapPlan(ResultSet rs, int rowNum) throws SQLException {
        String featuresJson = rs.getString("features_json");
        return new PlanCatalogResponse(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("target_role"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("currency"),
                rs.getInt("duration_days"),
                parseBenefits(featuresJson),
                rs.getInt("sort_order"),
                featureLimitService.featureIntOrNull(featuresJson, "maxJobs"),
                featureLimitService.featureIntOrNull(featuresJson, "maxCv"),
                featureLimitService.featureIntOrNull(featuresJson, "maxApplicationsPerDay"),
                featureLimitService.featureIntOrNull(featuresJson, "maxAiSessionsPerDay"),
                featureLimitService.featureIntOrNull(featuresJson, "listingPriority")
        );
    }

    private PlanRow mapPlanRow(ResultSet rs) throws SQLException {
        return new PlanRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("target_role"),
                rs.getBigDecimal("price"),
                rs.getString("currency"),
                rs.getInt("duration_days")
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> parseBenefits(String featuresJson) {
        if (!StringUtils.hasText(featuresJson)) {
            return List.of();
        }
        try {
            var node = objectMapper.readTree(featuresJson);
            var benefits = node.get("benefits");
            if (benefits != null && benefits.isArray()) {
                return objectMapper.convertValue(benefits, List.class);
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private String resolveTargetRole(User user) {
        return switch (user.getRoleEnum()) {
            case EMPLOYER -> "employer";
            case CANDIDATE -> "job_seeker";
            case ADMIN -> "all";
        };
    }

    private List<String> defaultBenefits(User user) {
        if (user.getRoleEnum() == User.UserRole.EMPLOYER) {
            return List.of("Hồ sơ công ty", "Đăng tin tuyển dụng", "Quản lý ứng viên");
        }
        return List.of("Hồ sơ ứng viên", "Tìm kiếm việc làm", "Ứng tuyển việc làm");
    }

    private void ensureUuid(String value, String label) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", label + " không hợp lệ");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record PlanRow(String id, String name, String targetRole, BigDecimal price, String currency, int durationDays) {
    }

    private record PaymentRow(String id, String subscriptionId, String status) {
    }

    private record SubscriptionRow(String id, UUID userId, String planId, String status) {
    }
}
