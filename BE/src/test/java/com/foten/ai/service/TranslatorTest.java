package com.foten.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 자리표시자 처리와 표기 변환만 본다. LLM 호출은 이 테스트의 대상이 아니라
 * 클라이언트를 null 로 둔다 — 아래 경로는 모두 호출 전이거나 호출 후다.
 */
class TranslatorTest {

    private final Translator translator = new Translator(null);

    @Test
    @DisplayName("숫자를 자리표시자로 바꾸고 원본을 순서대로 보관한다")
    void templatizeReplacesNumbersWithPlaceholders() {
        Translator.Template template =
                translator.templatize("이번 달 9월 1일~5일까지 834,650원을 썼습니다.");

        assertEquals("이번 달 {0}월 {1}일~{2}일까지 {3}원을 썼습니다.", template.text());
        assertEquals(List.of("9", "1", "5", "834,650"), template.tokens());
    }

    @Test
    @DisplayName("자리표시자 순서가 바뀌어도 번역을 살린다")
    void restoredSurvivesReorderedPlaceholders() {
        // 한국어 "9월 1일" 이 베트남어에서는 "ngay 1 thang 9" 라 자리가 뒤집힌다.
        // 예전 숫자 대조는 순서까지 봐서 이런 답변의 번역을 늘 버렸다.
        Translator.Template template = translator.templatize("9월 1일까지 834,650원");

        String result = translator.restored("{2} won den ngay {1} thang {0}", template, "vi");

        assertEquals("834.650 won den ngay 1 thang 9", result);
    }

    @Test
    @DisplayName("자리표시자가 빠지면 어디에 무엇을 넣을지 알 수 없어 버린다")
    void restoredRejectsMissingPlaceholder() {
        Translator.Template template = translator.templatize("834,650원");

        assertNull(translator.restored("834.650 won", template, "vi"));
    }

    @Test
    @DisplayName("같은 자리표시자가 두 번 나오면 버린다")
    void restoredRejectsDuplicatedPlaceholder() {
        Translator.Template template = translator.templatize("834,650원");

        assertNull(translator.restored("{0} won {0}", template, "vi"));
    }

    @Test
    @DisplayName("원문에 없는 번호를 지어내면 버린다")
    void restoredRejectsInventedPlaceholder() {
        Translator.Template template = translator.templatize("834,650원");

        assertNull(translator.restored("{0} won {1}", template, "vi"));
    }

    @Test
    @DisplayName("금액은 현지 자릿수 구분 기호로 바꾼다")
    void localizeUsesLocaleGroupingSeparator() {
        assertEquals("834.650", translator.localize("834,650", "vi"));
        assertEquals("21.811.216", translator.localize("21,811,216", "vi"));
    }

    @Test
    @DisplayName("소수는 소수점만 바꾸고 자릿수를 유지한다")
    void localizeSwapsDecimalMarkAndKeepsFractionDigits() {
        assertEquals("75,2", translator.localize("75.2", "vi"));
        assertEquals("19,256148", translator.localize("19.256148", "vi"));
    }

    @Test
    @DisplayName("앞자리 0은 자릿수를 맞춘 표기이므로 원문을 그대로 둔다")
    void localizeKeepsLeadingZeroAsIs() {
        assertEquals("09", translator.localize("09", "vi"));
        assertEquals("05", translator.localize("05", "vi"));
    }

    @Test
    @DisplayName("0으로 시작하는 소수는 값이므로 현지 표기로 바꾼다")
    void localizeFormatsDecimalStartingWithZero() {
        assertEquals("0,5", translator.localize("0.5", "vi"));
    }

    @Test
    @DisplayName("한국어 회원은 옮기지 않는다")
    void translateReturnsNullForKoreanMember() {
        assertNull(translator.translate("이번 달 목표 저축액", "ko"));
    }

    @Test
    @DisplayName("한국어 회원의 선택지는 실패가 아니라 옮길 것이 없는 것이다")
    void translateLinesReturnsNullsForKoreanMember() {
        List<String> result =
                translator.translateLines(List.of("식비 얼마 썼어?", "교통 얼마 썼어?"), "ko");

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertNull(result.get(1));
    }

    @Test
    @DisplayName("선택지가 없으면 번역할 것도 없다")
    void translateLinesReturnsEmptyForEmptyInput() {
        assertEquals(List.of(), translator.translateLines(List.of(), "vi"));
    }
}
