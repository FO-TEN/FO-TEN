package com.foten.ai.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 모델이 만든 문자열을 읽는 자리다. 모양을 믿지 않는 것이 이 클래스의 계약이고,
 * 못 읽으면 예외가 아니라 null 을 줘야 툴이 "모르겠다" 고 답할 수 있다.
 */
class ToolArgumentsTest {

    private static void assertSameValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    @DisplayName("인자 이름으로 숫자를 꺼낸다")
    void readsNumber() {
        assertSameValue("1000000", ToolArguments.number("{\"krwAmount\":1000000}", "krwAmount"));
    }

    @Test
    @DisplayName("큰 수도 정밀도가 깎이지 않는다")
    void keepsPrecisionForLargeNumbers() {
        // double 로 받으면 이 자릿수에서 값이 흔들린다. 목표 금액이 이 크기다.
        assertSameValue("420000000", ToolArguments.number("{\"foreignAmount\":420000000}", "foreignAmount"));
    }

    @Test
    @DisplayName("소수도 읽는다")
    void readsDecimal() {
        assertSameValue("19.256148", ToolArguments.number("{\"rate\":19.256148}", "rate"));
    }

    @Test
    @DisplayName("찾는 이름이 없으면 null")
    void returnsNullWhenNameNotFound() {
        assertNull(ToolArguments.number("{\"krwAmount\":1000}", "foreignAmount"));
    }

    @Test
    @DisplayName("숫자가 아니면 null - 모델이 금액을 말로 적어 보낼 수 있다")
    void returnsNullWhenNotANumber() {
        assertNull(ToolArguments.number("{\"krwAmount\":\"백만원\"}", "krwAmount"));
    }

    @Test
    @DisplayName("깨진 JSON 이면 예외를 올리지 않고 null")
    void returnsNullForMalformedJson() {
        assertNull(ToolArguments.number("{krwAmount:", "krwAmount"));
    }

    @Test
    @DisplayName("인자가 아예 없으면 null")
    void returnsNullForBlankInput() {
        assertNull(ToolArguments.number(null, "krwAmount"));
        assertNull(ToolArguments.number("", "krwAmount"));
    }
}
