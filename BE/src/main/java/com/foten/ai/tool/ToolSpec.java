package com.foten.ai.tool;

import com.foten.ai.llm.LlmTool;

import javax.tools.Tool;
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

    public LlmTool toLlmTool() {
        return LlmTool.of(name, description, parameters);
    }
}
