package com.foten.ai.llm;

import java.util.Map;
import java.util.function.Function;

public record LlmTool(String type, Function function) {
    public record Function(String name, String description, Map<String, Object> parameters) {}

    public static LlmTool of(String name, String description, Map<String, Object> parameters) {
        return new LlmTool("function", new Function(name, description, parameters));
    }
}
