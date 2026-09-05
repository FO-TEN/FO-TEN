package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpendingSuggestionProviderTest {

    private final SpendingSuggestionProvider provider = new SpendingSuggestionProvider();

    private static ChatContext spendingTurn() {
        ChatContext ctx = ChatContext.of(1L, "vi", "이번 달 얼마 썼어?");
        ctx.calledTools().add("getSpendingSummary");
        return ctx;
    }

    private static List<String> labelsOf(List<Suggestion> chips) {
        return chips.stream().map(Suggestion::labelKo).toList();
    }

    @Test
    @DisplayName("소비 툴을 부르지 않은 턴에는 칩을 붙이지 않는다")
    void returnsNothingWhenSpendingToolNotCalled() {
        ChatContext ctx = ChatContext.of(1L, "vi", "안녕하세요");

        assertTrue(provider.suggest(ctx, "안녕하세요. 무엇을 도와드릴까요?").isEmpty());
    }

    @Test
    @DisplayName("답변이 이미 말한 항목은 뺀다")
    void excludesCategoriesAlreadyMentioned() {
        List<Suggestion> chips = provider.suggest(spendingTurn(),
                "식비는 25,000원, 교통은 4,150원, 쇼핑은 5,500원입니다.");

        assertEquals(List.of("통신 얼마 썼어?", "기타 얼마 썼어?"), labelsOf(chips));
    }

    @Test
    @DisplayName("답변이 항목을 말하지 않으면 정해진 순서로 세 개까지만 준다")
    void returnsAtMostThreeInFixedOrder() {
        List<Suggestion> chips = provider.suggest(spendingTurn(), "834,650원을 썼습니다.");

        assertEquals(List.of("식비 얼마 썼어?", "교통 얼마 썼어?", "통신 얼마 썼어?"), labelsOf(chips));
    }

    @Test
    @DisplayName("답변이 없으면 칩을 붙이지 않는다")
    void returnsNothingWhenContentIsNull() {
        assertTrue(provider.suggest(spendingTurn(), null).isEmpty());
    }

    @Test
    @DisplayName("여기서 만드는 칩은 전부 탐색형이다")
    void marksEveryChipAsFollowup() {
        provider.suggest(spendingTurn(), "834,650원을 썼습니다.")
                .forEach(chip -> assertEquals(Suggestion.Kind.FOLLOWUP, chip.kind()));
    }
}
