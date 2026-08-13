package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.TaxCodeLookupResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxCodeLookupServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsCompanyAndCachesSuccessfulLookup() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer(200, """
                {
                  "code": "00",
                  "desc": "Success - Thành công",
                  "data": {
                    "id": "0316794479",
                    "name": "CÔNG TY TNHH CASSO",
                    "address": "Thành phố Hồ Chí Minh",
                    "status": "NNT đang hoạt động"
                  }
                }
                """, requests);

        TaxCodeLookupService service = service();
        TaxCodeLookupResponse first = service.lookup("0316794479");
        TaxCodeLookupResponse second = service.lookup("0316794479");

        assertThat(first.companyName()).isEqualTo("CÔNG TY TNHH CASSO");
        assertThat(first.address()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(first.status()).isEqualTo("NNT đang hoạt động");
        assertThat(second).isEqualTo(first);
        assertThat(requests).hasValue(1);
    }

    @Test
    void acceptsAndNormalizesBranchTaxCodeSeparator() throws IOException {
        startServer(200, """
                {
                  "code": "00",
                  "data": {
                    "id": "0316794479001",
                    "name": "CHI NHÁNH CÔNG TY TNHH CASSO"
                  }
                }
                """, new AtomicInteger());

        TaxCodeLookupResponse result = service().lookup("0316794479-001");

        assertThat(result.taxCode()).isEqualTo("0316794479001");
    }

    @Test
    void rejectsTaxCodeNotFoundAndCachesNegativeResult() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer(200, """
                {"code":"51","desc":"Tax not found - Mã số thuế không tồn tại","data":null}
                """, requests);
        TaxCodeLookupService service = service();

        assertInvalidTaxCode(() -> service.lookup("0000000000"));
        assertInvalidTaxCode(() -> service.lookup("0000000000"));
        assertThat(requests).hasValue(1);
    }

    @Test
    void rejectsMalformedTaxCodeWithoutCallingProvider() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer(200, "{}", requests);

        assertInvalidTaxCode(() -> service().lookup("ABC-123"));
        assertThat(requests).hasValue(0);
    }

    @Test
    void reportsProviderFailureSeparatelyFromInvalidTaxCode() throws IOException {
        startServer(500, "{}", new AtomicInteger());

        assertThatThrownBy(() -> service().lookup("0316794479"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("TAX_LOOKUP_UNAVAILABLE");
                });
    }

    @Test
    void treatsUnexpectedProviderPayloadAsUnavailable() throws IOException {
        startServer(200, "{\"code\":\"99\",\"data\":null}", new AtomicInteger());

        assertThatThrownBy(() -> service().lookup("0316794479"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("TAX_LOOKUP_UNAVAILABLE");
                });
    }

    private void assertInvalidTaxCode(ThrowingLookup lookup) {
        assertThatThrownBy(lookup::run)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TAX_CODE");
                });
    }

    private TaxCodeLookupService service() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
        return new TaxCodeLookupService(RestClient.builder(), baseUrl, clock);
    }

    private void startServer(int status, String responseBody, AtomicInteger requests) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/business", exchange -> {
            requests.incrementAndGet();
            respond(exchange, status, responseBody);
        });
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @FunctionalInterface
    private interface ThrowingLookup {
        void run();
    }
}
