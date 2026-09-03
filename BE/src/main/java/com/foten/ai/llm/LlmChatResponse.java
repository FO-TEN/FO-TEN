package com.foten.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(LlmMessage message){}

    public String firstContent() {
        if(choices == null || choices.isEmpty() || choices.get(0).message() == null ) {
            return null;
        }

        return choices.get(0).message().content();
    }

}
