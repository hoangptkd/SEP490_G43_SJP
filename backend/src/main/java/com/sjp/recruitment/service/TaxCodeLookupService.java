package com.sjp.recruitment.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.TaxCodeLookupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TaxCodeLookupService {

    private static final Pattern VIETNAM_TAX_CODE = Pattern.compile("\\d{10}(?:\\d{3})?");
    private static final Duration FOUND_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration NOT_FOUND_CACHE_TTL = Duration.ofMinutes(2);
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public TaxCodeLookupService(
            RestClient.Builder restClientBuilder,
            @Value("${app.tax-lookup.base-url:https://api.vietqr.io/v2}") String baseUrl
    ) {
        this(restClientBuilder, baseUrl, Clock.systemUTC());
    }

    TaxCodeLookupService(RestClient.Builder restClientBuilder, String baseUrl, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.clock = clock;
    }

    public TaxCodeLookupResponse lookup(String rawTaxCode) {
        String taxCode = normalize(rawTaxCode);
        if (!VIETNAM_TAX_CODE.matcher(taxCode).matches()) {
            throw invalidTaxCode("Mã số thuế phải gồm 10 chữ số hoặc 13 chữ số đối với đơn vị phụ thuộc.");
        }

        CacheEntry cached = cache.get(taxCode);
        Instant now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.requireResult();
        }
        if (cached != null) {
            cache.remove(taxCode, cached);
        }

        VietQrBusinessResponse response;
        try {
            response = restClient.get()
                    .uri("/business/{taxCode}", taxCode)
                    .retrieve()
                    .body(VietQrBusinessResponse.class);
        } catch (RestClientException exception) {
            log.warn("VietQR tax-code lookup failed for {}: {}", taxCode, exception.getMessage());
            throw lookupUnavailable();
        }

        if (response == null) {
            throw lookupUnavailable();
        }

        if ("51".equals(response.code())) {
            putCache(taxCode, CacheEntry.notFound(now.plus(NOT_FOUND_CACHE_TTL)));
            throw invalidTaxCode("Mã số thuế không tồn tại hoặc không hợp lệ.");
        }

        if (!"00".equals(response.code())
                || response.data() == null
                || response.data().name() == null
                || response.data().name().isBlank()) {
            log.warn("Unexpected VietQR response code {} for tax code {}", response.code(), taxCode);
            throw lookupUnavailable();
        }

        TaxCodeLookupResponse result = new TaxCodeLookupResponse(
                taxCode,
                response.data().name().trim(),
                response.data().address(),
                response.data().status()
        );
        putCache(taxCode, CacheEntry.found(result, now.plus(FOUND_CACHE_TTL)));
        return result;
    }

    public String normalize(String rawTaxCode) {
        if (rawTaxCode == null || rawTaxCode.isBlank()) {
            return "";
        }
        return rawTaxCode.trim().replaceAll("[\\s-]", "");
    }

    private void putCache(String taxCode, CacheEntry entry) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(item -> !item.getValue().expiresAt().isAfter(clock.instant()));
        }
        if (cache.size() < MAX_CACHE_ENTRIES) {
            cache.put(taxCode, entry);
        }
    }

    private ApiException invalidTaxCode(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TAX_CODE", message);
    }

    private ApiException lookupUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TAX_LOOKUP_UNAVAILABLE",
                "Không thể xác minh mã số thuế lúc này. Vui lòng thử lại sau."
        );
    }

    private record CacheEntry(TaxCodeLookupResponse result, boolean found, Instant expiresAt) {
        static CacheEntry found(TaxCodeLookupResponse result, Instant expiresAt) {
            return new CacheEntry(result, true, expiresAt);
        }

        static CacheEntry notFound(Instant expiresAt) {
            return new CacheEntry(null, false, expiresAt);
        }

        TaxCodeLookupResponse requireResult() {
            if (!found) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "INVALID_TAX_CODE",
                        "Mã số thuế không tồn tại hoặc không hợp lệ."
                );
            }
            return result;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VietQrBusinessResponse(String code, String desc, VietQrBusinessData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VietQrBusinessData(
            String id,
            String name,
            String internationalName,
            String shortName,
            String address,
            String status
    ) {
    }
}
