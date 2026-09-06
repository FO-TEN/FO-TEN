package com.foten.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoreanScriptTest {

    @Test
    void 한국어와_숫자_기호_라틴은_정상이다() {
        String ok = "KB Global Star 적금에 매달 ₩500,000원을 넣어요. 금리는 2.5%, 기준일 09.07 · VND 환산 ≈ 104,225,995₫입니다.";
        assertTrue(KoreanScript.isClean(ok));
        assertEquals(0, KoreanScript.foreignCount(ok));
    }

    @Test
    void 구자라트_문자가_섞이면_잡는다() {
        String bad = "목표를 세운 지 1개월째입니다. યોજના? 달성률은 60.0%입니다.";
        assertFalse(KoreanScript.isClean(bad));
        assertEquals(5, KoreanScript.foreignCount(bad));
    }

    @Test
    void 태국_문자와_한자도_잡는다() {
        assertFalse(KoreanScript.isClean("이번 달 저축액은 ดี 517,647원입니다."));
        assertFalse(KoreanScript.isClean("이번 달 貯蓄 금액입니다."));
    }

    @Test
    void 걷어내면_한국어만_남고_공백이_정리된다() {
        String bad = "목표를 세운 지 1개월째입니다. યોજના? 달성률은 60.0%입니다.";
        assertEquals("목표를 세운 지 1개월째입니다.? 달성률은 60.0%입니다.", KoreanScript.strip(bad));
        assertTrue(KoreanScript.isClean(KoreanScript.strip(bad)));
    }

    @Test
    void 빈값은_정상으로_본다() {
        assertTrue(KoreanScript.isClean(null));
        assertTrue(KoreanScript.isClean(""));
    }
}
