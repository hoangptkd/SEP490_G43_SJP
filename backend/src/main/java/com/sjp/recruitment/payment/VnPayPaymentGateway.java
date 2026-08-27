package com.sjp.recruitment.payment;

import com.sjp.recruitment.config.VnPayProperties;
import com.sjp.recruitment.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VnPayPaymentGateway {

    private final VnPayProperties properties;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.backend-base-url}")
    private String backendBaseUrl;

    public String createPaymentUrl(String orderId, long amountVnd, String orderInfo, String clientIp) {
        ensureConfigured();

        long amount = amountVnd * 100; // VNPay uses VND * 100
        String createDate = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String expireDate = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(15)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", orderId.replace("-", ""));
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", frontendBaseUrl + "/payment/result?paymentId=" + orderId + "&gateway=vnpay");
        params.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        String hashData = buildHashData(params);
        String secureHash = hmacSha512(properties.getHashSecret(), hashData);
        params.put("vnp_SecureHash", secureHash);

        String query = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        return properties.getPayUrl() + "?" + query;
    }

    public boolean verifySignature(Map<String, String> params) {
        if (!StringUtils.hasText(properties.getHashSecret())) {
            return false;
        }
        String received = params.get("vnp_SecureHash");
        if (!StringUtils.hasText(received)) {
            return false;
        }
        Map<String, String> filtered = new HashMap<>(params);
        filtered.remove("vnp_SecureHash");
        filtered.remove("vnp_SecureHashType");
        String hashData = buildHashData(filtered);
        String expected = hmacSha512(properties.getHashSecret(), hashData);
        return expected.equalsIgnoreCase(received);
    }

    public boolean isSuccess(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode")) || "00".equals(params.get("vnp_TransactionStatus"));
    }

    public String resolveOrderId(Map<String, String> params, String fallbackUuid) {
        // We send txnRef without dashes; try to restore UUID if fallback matches
        String txnRef = params.get("vnp_TxnRef");
        if (!StringUtils.hasText(txnRef)) {
            return fallbackUuid;
        }
        if (StringUtils.hasText(fallbackUuid) && fallbackUuid.replace("-", "").equalsIgnoreCase(txnRef)) {
            return fallbackUuid;
        }
        // If payment id was stored as gateway_order_id = uuid, caller should pass paymentId query param
        return fallbackUuid != null ? fallbackUuid : txnRef;
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPAY_DISABLED", "Cổng thanh toán VNPay chưa được bật");
        }
        if (!StringUtils.hasText(properties.getTmnCode()) || !StringUtils.hasText(properties.getHashSecret())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPAY_NOT_CONFIGURED",
                    "Chưa cấu hình VNPay. Vui lòng thêm VNPAY_TMN_CODE và VNPAY_HASH_SECRET vào .env");
        }
    }

    private String buildHashData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> StringUtils.hasText(e.getValue()))
                .map(e -> e.getKey() + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VNPAY_SIGNATURE_ERROR", "Không tạo được chữ ký VNPay");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
