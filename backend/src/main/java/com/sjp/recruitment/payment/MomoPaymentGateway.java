package com.sjp.recruitment.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.MomoProperties;
import com.sjp.recruitment.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MomoPaymentGateway {

    private final MomoProperties momoProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.backend-base-url}")
    private String backendBaseUrl;

    public MomoCreateResult createPayment(String orderId, long amountVnd, String orderInfo) {
        if (!momoProperties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MOMO_DISABLED", "Cổng thanh toán MoMo chưa được bật");
        }

        String requestId = UUID.randomUUID().toString();
        String extraData = "";
        String requestType = "captureWallet";
        String ipnUrl = backendBaseUrl + "/payments/momo/ipn";
        String redirectUrl = frontendBaseUrl + "/payment/result?paymentId=" + orderId;

        String rawSignature = "accessKey=" + momoProperties.getAccessKey()
                + "&amount=" + amountVnd
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + momoProperties.getPartnerCode()
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        String signature = hmacSha256(rawSignature, momoProperties.getSecretKey());

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("partnerCode", momoProperties.getPartnerCode());
            body.put("partnerName", "Smart Recruitment Portal");
            body.put("storeId", "SJP");
            body.put("requestId", requestId);
            body.put("amount", amountVnd);
            body.put("orderId", orderId);
            body.put("orderInfo", orderInfo);
            body.put("redirectUrl", redirectUrl);
            body.put("ipnUrl", ipnUrl);
            body.put("lang", "vi");
            body.put("requestType", requestType);
            body.put("autoCapture", true);
            body.put("extraData", extraData);
            body.put("signature", signature);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(momoProperties.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode node = objectMapper.readTree(response.body());
            int resultCode = node.path("resultCode").asInt(-1);
            if (resultCode != 0) {
                String message = node.path("message").asText("Không tạo được giao dịch MoMo");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MOMO_CREATE_FAILED", message);
            }

            return new MomoCreateResult(
                    node.path("payUrl").asText(),
                    requestId,
                    node
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MOMO_CREATE_FAILED", "Không kết nối được MoMo: " + ex.getMessage());
        }
    }

    public boolean verifyIpnSignature(Map<String, Object> payload) {
        String accessKey = stringValue(payload.get("accessKey"));
        String amount = stringValue(payload.get("amount"));
        String extraData = stringValue(payload.get("extraData"));
        String message = stringValue(payload.get("message"));
        String orderId = stringValue(payload.get("orderId"));
        String orderInfo = stringValue(payload.get("orderInfo"));
        String orderType = stringValue(payload.get("orderType"));
        String partnerCode = stringValue(payload.get("partnerCode"));
        String payType = stringValue(payload.get("payType"));
        String requestId = stringValue(payload.get("requestId"));
        String responseTime = stringValue(payload.get("responseTime"));
        String resultCode = stringValue(payload.get("resultCode"));
        String transId = stringValue(payload.get("transId"));
        String signature = stringValue(payload.get("signature"));

        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&message=" + message
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + orderType
                + "&partnerCode=" + partnerCode
                + "&payType=" + payType
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + transId;

        String expected = hmacSha256(rawSignature, momoProperties.getSecretKey());
        return expected.equals(signature);
    }

    public boolean isSuccessResultCode(Object resultCode) {
        return "0".equals(stringValue(resultCode));
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
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SIGNATURE_ERROR", "Không tạo được chữ ký MoMo");
        }
    }

    public record MomoCreateResult(String payUrl, String requestId, JsonNode rawResponse) {
    }

    public JsonNode queryPayment(String orderId, String requestId) {
        String rawSignature = "accessKey=" + momoProperties.getAccessKey()
                + "&orderId=" + orderId
                + "&partnerCode=" + momoProperties.getPartnerCode()
                + "&requestId=" + requestId;
        String signature = hmacSha256(rawSignature, momoProperties.getSecretKey());

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("partnerCode", momoProperties.getPartnerCode());
            body.put("requestId", requestId);
            body.put("orderId", orderId);
            body.put("lang", "vi");
            body.put("signature", signature);

            String endpoint = momoProperties.getEndpoint().replace("/create", "/query");
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MOMO_QUERY_FAILED", "Không truy vấn được MoMo");
        }
    }
}
