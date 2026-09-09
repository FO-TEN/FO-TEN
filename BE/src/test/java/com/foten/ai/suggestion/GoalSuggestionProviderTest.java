package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalSuggestionProviderTest {

    private static final String SHORTAGE_ANSWER =
            "지금처럼 쓰면 이번 달 예상 저축액은 889,729원입니다. 이번 달에 모아야 하는 금액은 1,098,169원이라 202,558원이 부족합니다.";
    private static final String CUT_ANSWER =
            "가장 많이 줄일 수 있는 항목은 쇼핑입니다. 한 달에 242,674원까지 줄일 수 있습니다.";

    private final GoalSuggestionProvider provider = new GoalSuggestionProvider();

    private static ChatContext turn(String... tools) {
        ChatContext ctx = ChatContext.of(1L, "vi", "이번 달에 나 얼마나 모을 수 있을까?");
        ctx.calledTools().addAll(List.of(tools));
        return ctx;
    }

    @Test
    @DisplayName("부족하다고 답한 진단 턴에는 어디서 줄일지 물을 칩을 준다")
    void offersWhereToCutAfterShortage() {
        List<Suggestion> chips = provider.suggest(turn("diagnoseGoal"), SHORTAGE_ANSWER);

        assertEquals(List.of("어디서 소비를 줄일 수 있을까?"), chips.stream().map(Suggestion::labelKo).toList());
        assertEquals(Suggestion.Kind.FOLLOWUP, chips.get(0).kind());
    }

    @Test
    @DisplayName("절감 여력을 이미 말한 답에는 붙이지 않는다")
    void returnsNothingAfterCutAnswer() {
        assertTrue(provider.suggest(turn("diagnoseGoal"), CUT_ANSWER).isEmpty());
    }

    @Test
    @DisplayName("부족하지 않은 답에는 붙이지 않는다")
    void returnsNothingWhenNotShort() {
        assertTrue(provider.suggest(turn("diagnoseGoal"), "지금처럼 쓰면 이번 달 예상 저축액은 1,573,396원입니다.").isEmpty());
    }

    @Test
    @DisplayName("진단 툴을 부르지 않은 턴에는 붙이지 않는다")
    void returnsNothingWhenDiagnoseToolNotCalled() {
        assertTrue(provider.suggest(turn("getRoadmapStatus"), SHORTAGE_ANSWER).isEmpty());
    }

    @Test
    @DisplayName("답변이 없으면 붙이지 않는다")
    void returnsNothingWhenContentIsNull() {
        assertTrue(provider.suggest(turn("diagnoseGoal"), null).isEmpty());
    }
}
