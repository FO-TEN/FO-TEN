package com.foten.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foten.common.ExternalApiException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;

    public LlmClient(
            ObjectMapper objectMapper,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("llm.api-key 가 비어 있습니다. LLM_API_KEY 환경변수를 확인하세요.");
        }
    }

    public String call(List<LlmMessage> messages) {
        HttpResponse<String> response = send(LlmChatRequest.of(model, messages));
        if(response.statusCode() != 200) {
            log.error("LLM 호출 실패: status={}", response.statusCode());
            throw new ExternalApiException("LLM 응답을 받지 못했습니다.");
        }

        String content = readContent(response.body());
        if(content == null || content.isBlank()) {
            throw new ExternalApiException("LLM 응답이 비어 있습니다.");
        }
        return content;
    }

    private HttpResponse<String> send(LlmChatRequest body) {
        HttpRequest request;

        try{
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8
                    ))
                    .build();
        }
        catch(JsonProcessingException e) {
            throw new ExternalApiException("LLM 요청을 만들지 못했습니다.", e);
        }

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            throw new ExternalApiException("LLM 서버에 연결하지 못했습니다.", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("LLM 호출이 중단되었습니다.", e);
        }
    }

    private String readContent(String body) {
        try {
            return objectMapper.readValue(body, LlmChatResponse.class).firstContent();
        }
        catch (JsonProcessingException e) {
            throw new ExternalApiException("LLM 응답을 해석하지 못했습니다.", e);
        }
    }
}
