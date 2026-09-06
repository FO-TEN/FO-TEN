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

    // 정해진 값 중 하나를 받는 툴. 생략할 수 있어서 required 에 넣지 않는다
    // (고를 상황이 아닌데 억지로 하나를 채워 보내는 것을 막는다).
    public static ToolSpec optionalEnum(String name, String description,
                                        String argument, String argumentDescription,
                                        List<String> values,
                                        BiFunction<String, ToolContext, String> executor) {
        Map<String, Object> argumentSchema = new LinkedHashMap<>();
        argumentSchema.put("type", "string");
        argumentSchema.put("enum", values);
        argumentSchema.put("description", argumentDescription);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(argument, argumentSchema);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("additionalProperties", false);

        return new ToolSpec(name, description, parameters, executor);
    }

    // 문자열 배열 인자를 받는 툴
    public static ToolSpec stringList(String name, String description,
                                      String argument, String argumentDescription,
                                      BiFunction<String, ToolContext, String> executor) {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "string");

        Map<String, Object> argumentSchema = new LinkedHashMap<>();
        argumentSchema.put("type", "array");
        argumentSchema.put("items", itemSchema);
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

    // 배열 하나와, 정해진 값 중 하나를 받는(생략 가능) 인자를 함께 받는 툴.
    // 구간이 바뀌는 달의 우대조건 제출처럼 고른 목록과 방식을 한 번에 보내야 할 때 쓴다.
    public static ToolSpec stringListWithOptionalEnum(
            String name, String description,
            String listArgument, String listDescription,
            String enumArgument, String enumDescription, List<String> enumValues,
            BiFunction<String, ToolContext, String> executor) {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "string");

        Map<String, Object> listSchema = new LinkedHashMap<>();
        listSchema.put("type", "array");
        listSchema.put("items", itemSchema);
        listSchema.put("description", listDescription);

        Map<String, Object> enumSchema = new LinkedHashMap<>();
        enumSchema.put("type", "string");
        enumSchema.put("enum", enumValues);
        enumSchema.put("description", enumDescription);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(listArgument, listSchema);
        properties.put(enumArgument, enumSchema);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of(listArgument));
        parameters.put("additionalProperties", false);

        return new ToolSpec(name, description, parameters, executor);
    }

    public LlmTool toLlmTool() {
        return LlmTool.of(name, description, parameters);
    }
}
