package com.foten.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            LlmMessage message,
            @JsonProperty("finish_reason") String finishReason
    ){}

    public Choice firstChoice() {
        return (choices == null || choices.isEmpty()) ? null : choices.get(0);
    }

    public String firstContent() {
        Choice choice = firstChoice();
        return (choice == null || choice.message() == null) ? null : choice.message().content();
    }

}
