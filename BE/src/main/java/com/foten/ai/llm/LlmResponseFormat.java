package com.foten.ai.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record LlmResponseFormat (
        String type,
        @JsonProperty("json_schema")Schema jsonSchema
){
    public record Schema(String name, boolean strict, Map<String, Object> schema) {}

    public static LlmResponseFormat of(Class<?> entityType) {
        return new LlmResponseFormat(
                "json_schema",
                new Schema(entityType.getSimpleName(), true, JsonSchema.of(entityType))
        );
    }
}
