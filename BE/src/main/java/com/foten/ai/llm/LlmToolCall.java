package com.foten.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.function.Function;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmToolCall(String id, String type, Function function){

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Function(String name, String arguments) {}
}
