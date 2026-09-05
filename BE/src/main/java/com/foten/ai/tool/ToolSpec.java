package com.foten.ai.tool;

import com.foten.ai.llm.LlmTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public record ToolSpec(
        String name,
        String description,
        Map<String, Object> parameters,
        BiFunction<String, ToolContext, String> executor
) {
    public static ToolSpec noArgs(String name, String description, BiFunction<String, ToolContext, String> executor) {
        return new ToolSpec(
                name,
                description,
                Map.of("type", "object", "properties", Map.of()),
                executor
        );
    }

    // 숫자 인자를 받는 툴
    public static ToolSpec oneNumber(String name, String description,
                                     String argument, String argumentDescription,
                                     BiFunction<String, ToolContext, String> executor) {
        Map<String, Object> argumentSchema = new LinkedHashMap<>();
        argumentSchema.put("type", "number");
        argumentSchema.put("description", argumentDescription);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(argument, argumentSchema);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of(argument));
        parameters.put("additionalProperties", false);

        return new ToolSpec(name, description, parameters, executor);
    }

    public LlmTool toLlmTool() {
        return LlmTool.of(name, description, parameters);
    }
}
