package com.foten.common.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;


// 배치 실패 같은 운영 알림을 팀 슬랙 채널로 전송
@Component
public class SlackNotifier {
    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final HttpClient httpClient;

    public SlackNotifier(
            ObjectMapper objectMapper,
            @Value("${notify.slack.webhook-url:}") String webhookUrl
    ) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

        if(!isConfigured()) {
            log.info("notify.slack.webhook-url이 비어 있어 운영 알림을 보내지 않습니다.");
        }
    }

    // 알림 전송이 실패해도 호출한 쪽의 흐름을 끊지 않는다.
    public void alert(String message) {
        if(!isConfigured()) {
            return;
        }

        try{
            HttpResponse<String> response = httpClient.send(build(message),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if(response.statusCode() != 200) {
                log.warn("슬랙 알림 전송 실패: status={}", response.statusCode());
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("슬랙 알림 전송이 중단되었습니다.");
        }
        catch (Exception e) {
            log.warn("슬랙 알림을 보내지 못했습니다: {}", e.getMessage());
        }
    }
    private boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    private HttpRequest build(String message) throws JsonProcessingException {
        String body = objectMapper.writeValueAsString(Map.of("text", "[FO:TEN] " + message));

        return HttpRequest.newBuilder(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }
}
