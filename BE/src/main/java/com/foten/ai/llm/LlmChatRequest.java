package com.foten.ai.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmChatRequest(
        String model,
        List<LlmMessage> messages,
        @JsonProperty("reasoning_effort") String reasoningEffort,
        List<LlmTool> tools,
        @JsonProperty("response_format") LlmResponseFormat responseFormat
) {
    public static LlmChatRequest of(String model, List<LlmMessage> messages) {
        // 추론 수준 : none -> 계산은 java 엔진에서 진행되기 때문
        return new LlmChatRequest(model, messages, "none", null, null);
    }

    public static LlmChatRequest withTools(String model, List<LlmMessage> messages, List<LlmTool> tools) {
        return new LlmChatRequest(model, messages, "none", tools, null);
    }

    public static LlmChatRequest withSchema(String model, List<LlmMessage> messages, Class<?> entityType) {
        return new LlmChatRequest(model, messages, "none", null, LlmResponseFormat.of(entityType));
    }
}
