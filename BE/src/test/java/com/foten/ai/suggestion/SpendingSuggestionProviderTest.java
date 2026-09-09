package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpendingSuggestionProviderTest {

    private static final String BREAKDOWN = "전체 소비 내역 보기";

    private final SpendingSuggestionProvider provider = new SpendingSuggestionProvider();

    private static ChatContext turn(String... tools) {
        ChatContext ctx = ChatContext.of(1L, "vi", "이번 달 얼마 썼어?");
        ctx.calledTools().addAll(List.of(tools));
        return ctx;
    }

    private static List<String> labelsOf(List<Suggestion> chips) {
        return chips.stream().map(Suggestion::labelKo).toList();
    }

    @Test
    @DisplayName("소비 툴을 부르지 않은 턴에는 칩을 붙이지 않는다")
    void returnsNothingWhenSpendingToolNotCalled() {
        assertTrue(provider.suggest(turn(), "안녕하세요. 무엇을 도와드릴까요?").isEmpty());
    }

    @Test
    @DisplayName("소비 조회 답 뒤에는 전체 내역 칩 하나만 준다")
    void offersBreakdownAfterSpendingAnswer() {
        List<Suggestion> chips = provider.suggest(turn("getSpendingSummary"), "이번 달 식비는 60,000원입니다.");

        assertEquals(List.of(BREAKDOWN), labelsOf(chips));
    }

    @Test
    @DisplayName("절감 여력을 말한 진단 답 뒤에도 전체 내역 칩을 준다")
    void offersBreakdownAfterCutAnswer() {
        List<Suggestion> chips = provider.suggest(turn("diagnoseGoal"),
                "가장 많이 줄일 수 있는 항목은 쇼핑입니다. 한 달에 242,674원까지 줄일 수 있습니다.");

        assertEquals(List.of(BREAKDOWN), labelsOf(chips));
    }

    @Test
    @DisplayName("절감 여력을 말하지 않은 진단 답에는 붙이지 않는다")
    void returnsNothingAfterDiagnosisWithoutCuts() {
        List<Suggestion> chips = provider.suggest(turn("diagnoseGoal"),
                "지금처럼 쓰면 이번 달 예상 저축액은 889,729원입니다. 202,558원이 부족합니다.");

        assertTrue(chips.isEmpty());
    }

    @Test
    @DisplayName("전체 내역을 이미 보여준 턴에는 칩을 붙이지 않는다")
    void returnsNothingAfterBreakdown() {
        ChatContext ctx = turn("getSpendingSummary", "getSpendingBreakdown");

        assertTrue(provider.suggest(ctx, "이번 달 834,650원 쓰셨습니다.").isEmpty());
    }

    @Test
    @DisplayName("답변이 없으면 칩을 붙이지 않는다")
    void returnsNothingWhenContentIsNull() {
        assertTrue(provider.suggest(turn("getSpendingSummary"), null).isEmpty());
    }

    @Test
    @DisplayName("여기서 만드는 칩은 탐색형이다")
    void marksChipAsFollowup() {
        provider.suggest(turn("getSpendingSummary"), "834,650원을 썼습니다.")
                .forEach(chip -> assertEquals(Suggestion.Kind.FOLLOWUP, chip.kind()));
    }
}
