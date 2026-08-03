package com.sjp.recruitment.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.PayOsProperties;
import com.sjp.recruitment.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class PayOsPaymentGateway {

    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    public PayOsCreateResult createPayment(String paymentId, long amountVnd, String description) {
        ensureConfigured();

        long orderCode = nextOrderCode();
        String returnUrl = frontendBaseUrl + "/payment/result?paymentId=" + paymentId;
        String cancelUrl = frontendBaseUrl + "/payment/result?paymentId=" + paymentId + "&cancelled=1";
        String shortDesc = shorten(description, 25);

        String rawSignature = "amount=" + amountVnd
                + "&cancelUrl=" + cancelUrl
                + "&description=" + shortDesc
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        String signature = hmacSha256(rawSignature, payOsProperties.getChecksumKey());

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderCode", orderCode);
            body.put("amount", amountVnd);
            body.put("description", shortDesc);
            body.put("returnUrl", returnUrl);
            body.put("cancelUrl", cancelUrl);
            body.put("signature", signature);
            body.put("items", List.of(Map.of(
                    "name", shortDesc,
                    "quantity", 1,
                    "price", amountVnd
            )));

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(payOsProperties.getEndpoint()) + "/v2/payment-requests"))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", payOsProperties.getClientId())
                    .header("x-api-key", payOsProperties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            String code = root.path("code").asText("");
            if (!"00".equals(code)) {
                String message = root.path("desc").asText("Không tạo được link PayOS");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYOS_CREATE_FAILED", message);
            }

            JsonNode data = root.path("data");
            String checkoutUrl = data.path("checkoutUrl").asText(null);
            if (!StringUtils.hasText(checkoutUrl)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYOS_CREATE_FAILED", "PayOS không trả checkoutUrl");
            }
            return new PayOsCreateResult(
                    checkoutUrl,
                    orderCode,
                    data.path("paymentLinkId").asText(null),
                    data.path("qrCode").asText(null),
                    root
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYOS_CREATE_FAILED",
                    "Không kết nối được PayOS: " + ex.getMessage());
        }
    }

    public JsonNode queryPayment(String orderCodeOrPaymentLinkId) {
        ensureConfigured();
        if (!StringUtils.hasText(orderCodeOrPaymentLinkId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER", "Thiếu mã đơn PayOS");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(payOsProperties.getEndpoint())
                            + "/v2/payment-requests/" + orderCodeOrPaymentLinkId))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", payOsProperties.getClientId())
                    .header("x-api-key", payOsProperties.getApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYOS_QUERY_FAILED",
                    "Không truy vấn được PayOS: " + ex.getMessage());
        }
    }

    public boolean isPaidStatus(JsonNode queryRoot) {
        if (queryRoot == null) {
            return false;
        }
        if (!"00".equals(queryRoot.path("code").asText(""))) {
            return false;
        }
        String status = queryRoot.path("data").path("status").asText("");
        return "PAID".equalsIgnoreCase(status);
    }

    public boolean verifyWebhookSignature(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        String signature = stringValue(payload.get("signature"));
        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap) || !StringUtils.hasText(signature)) {
            return false;
        }
        Map<String, String> flat = new TreeMap<>();
        for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            flat.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        String expected = hmacSha256(String.join("&", parts), payOsProperties.getChecksumKey());
        return expected.equalsIgnoreCase(signature);
    }

    public boolean isSuccess(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        if (Boolean.TRUE.equals(payload.get("success"))) {
            return true;
        }
        if ("00".equals(stringValue(payload.get("code")))) {
            Object data = payload.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                return "00".equals(stringValue(dataMap.get("code")));
            }
            return true;
        }
        return false;
    }

    public Long extractOrderCode(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object orderCode = dataMap.get("orderCode");
            if (orderCode instanceof Number number) {
                return number.longValue();
            }
            if (orderCode != null && StringUtils.hasText(String.valueOf(orderCode))) {
                try {
                    return Long.parseLong(String.valueOf(orderCode));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public String extractReference(Map<String, Object> payload) {
        Object data = payload == null ? null : payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            String reference = stringValue(dataMap.get("reference"));
            if (StringUtils.hasText(reference)) {
                return reference;
            }
            return stringValue(dataMap.get("paymentLinkId"));
        }
        return null;
    }

    private void ensureConfigured() {
        if (!payOsProperties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYOS_DISABLED", "Cổng thanh toán PayOS chưa được bật");
        }
        if (!StringUtils.hasText(payOsProperties.getClientId())
                || !StringUtils.hasText(payOsProperties.getApiKey())
                || !StringUtils.hasText(payOsProperties.getChecksumKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYOS_NOT_CONFIGURED",
                    "Thiếu cấu hình PayOS (PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY)");
        }
    }

    private long nextOrderCode() {
        long base = Instant.now().toEpochMilli() % 1_000_000_000_000L;
        int suffix = ThreadLocalRandom.current().nextInt(100, 999);
        return base * 1000 + suffix;
    }

    private String shorten(String value, int max) {
        String text = StringUtils.hasText(value) ? value.trim() : "Thanh toan SJP";
        text = text.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(text)) {
            text = "Thanh toan SJP";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String trimSlash(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "https://api-merchant.payos.vn";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SIGNATURE_ERROR", "Không tạo được chữ ký PayOS");
        }
    }

    public record PayOsCreateResult(
            String checkoutUrl,
            long orderCode,
            String paymentLinkId,
            String qrCode,
            JsonNode rawResponse
    ) {
    }
}
