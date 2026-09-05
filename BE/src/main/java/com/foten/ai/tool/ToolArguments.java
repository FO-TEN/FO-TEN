package com.foten.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public final class ToolArguments {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolArguments() {}

    public static BigDecimal number(String arguments, String name) {
        if(arguments == null || arguments.isBlank()) {
            return null;
        }

        try{
            JsonNode value = MAPPER.readTree(arguments).get(name);
            if(value == null || !value.isNumber()) {
                return null;
            }
            return value.decimalValue();
        }
        catch (Exception e) {
            // 인자 내용은 남기지 않는다. 금액이 로그에 찍힌다.
            log.warn("툴 인자를 읽지 못했습니다. name={}", name);
            return null;
        }
    }
}
