package com.foten.ai.llm;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonSchema {
    private JsonSchema() {}

    public static Map<String, Object> of(Class<?> type) {
        if(!type.isRecord()) {
            throw new IllegalArgumentException("record만 스키마로 만들 수 있습니다: " + type.getName());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for(RecordComponent component: type.getRecordComponents()) {
            properties.put(component.getName(), property(component));
            required.add(component.getName());
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> property(RecordComponent component) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", jsonType(component.getType()));

        Describe describe = component.getAnnotation(Describe.class);
        if (describe != null) {
            property.put("description", describe.value());
        }
        return property;
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == BigDecimal.class) {
            return "number";
        }
        throw new IllegalArgumentException(
                "스키마로 만들 수 없는 타입입니다: " + type.getName() + ". String·정수·실수·boolean 만 지원합니다.");
    }
}
