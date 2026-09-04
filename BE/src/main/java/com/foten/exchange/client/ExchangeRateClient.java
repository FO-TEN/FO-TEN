package com.foten.exchange.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foten.common.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class ExchangeRateClient {
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String BASE_CURRENCY = "KRW";
    private static final String RESULT_SUCCESS = "success";

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final HttpClient httpClient;

    public ExchangeRateClient(
            ObjectMapper objectMapper,
            @Value("${exchange.rate.base-url}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public Map<String, BigDecimal> fetchRates() {
        HttpResponse<String> response = send();

        if(response.statusCode() != 200) {
            log.error("환율 API 호출 실패: status={}", response.statusCode());
            throw new ExternalApiException("환율을 받아오지 못했습니다");
        }

        ExchangeRateApiResponse body = readResponse(response.body());

        if(!RESULT_SUCCESS.equals(body.result()) || body.rates() == null || body.rates().isEmpty()) {
            throw new ExternalApiException("환율 응답이 비어 있습니다. result=" + body.result());
        }

        log.info("환율 {}종 수신. 고시 시각 {}", body.rates().size(), body.lastUpdateUtc());
        return body.rates();
    }

    private HttpResponse<String> send() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + BASE_CURRENCY))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            throw new ExternalApiException("환율 서버에 연결하지 못했습니다.", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("환율 호출이 중단되었습니다.", e);
        }
    }

    private ExchangeRateApiResponse readResponse(String body) {
        try {
            return objectMapper.readValue(body, ExchangeRateApiResponse.class);
        }
        catch (JsonProcessingException e) {
            throw new ExternalApiException("환율 응답을 해석하지 못했습니다.", e);
        }
    }
}
