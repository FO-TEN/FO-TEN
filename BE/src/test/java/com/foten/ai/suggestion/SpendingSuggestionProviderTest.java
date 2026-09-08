package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import com.foten.spending.domain.CategoryTotal;
import com.foten.spending.domain.MonthlySpending;
import com.foten.spending.service.SpendingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpendingSuggestionProviderTest {

    // 시드 계정(nguyen01)의 이번 달 순서. 주거 > 기타 > 통신 > 식비 > 쇼핑 > 교통
    private static final List<CategoryTotal> BY_AMOUNT = List.of(
            new CategoryTotal("주거", new BigDecimal("600000")),
            new CategoryTotal("기타", new BigDecimal("100000")),
            new CategoryTotal("통신", new BigDecimal("100000")),
            new CategoryTotal("식비", new BigDecimal("25000")),
            new CategoryTotal("쇼핑", new BigDecimal("5500")),
            new CategoryTotal("교통", new BigDecimal("4150")));

    private final SpendingQueryService spendingQueryService = mock(SpendingQueryService.class);
    private final SpendingSuggestionProvider provider = new SpendingSuggestionProvider(spendingQueryService);

    @BeforeEach
    void stubThisMonth() {
        when(spendingQueryService.getMonthlySpending(anyLong(), anyInt())).thenReturn(spending(BY_AMOUNT));
    }

    private static MonthlySpending spending(List<CategoryTotal> categoryTotals) {
        return new MonthlySpending(YearMonth.of(2026, 9), 8,
                new BigDecimal("800000"), Map.of(),
                new BigDecimal("34650"), Map.of(),
                categoryTotals);
    }

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
                "기타는 100,000원, 통신은 100,000원입니다.");

        assertEquals(List.of("전체 내역 볼래", "주거 얼마 썼어?", "식비 얼마 썼어?"), labelsOf(chips));
    }

    @Test
    @DisplayName("남은 항목 중 이번 달에 많이 쓴 순으로 둘까지만 준다")
    void picksTheNextBiggestCategories() {
        List<Suggestion> chips = provider.suggest(spendingTurn(), "834,650원을 썼습니다.");

        assertEquals(List.of("전체 내역 볼래", "주거 얼마 썼어?", "기타 얼마 썼어?"), labelsOf(chips));
    }

    @Test
    @DisplayName("1위를 말한 답변 뒤에는 2·3위를 물어볼 자리를 준다")
    void offersRunnersUpAfterTheTopCategory() {
        List<Suggestion> chips = provider.suggest(spendingTurn(), "가장 큰 항목은 주거로 600,000원입니다.");

        assertEquals(List.of("전체 내역 볼래", "기타 얼마 썼어?", "통신 얼마 썼어?"), labelsOf(chips));
    }

    @Test
    @DisplayName("금액을 읽지 못하면 고정 순서로 물러선다")
    void fallsBackToFixedOrderWhenAmountsAreUnavailable() {
        when(spendingQueryService.getMonthlySpending(anyLong(), anyInt()))
                .thenThrow(new IllegalStateException("조회 실패"));

        List<Suggestion> chips = provider.suggest(spendingTurn(), "834,650원을 썼습니다.");

        assertEquals(List.of("전체 내역 볼래", "식비 얼마 썼어?", "교통 얼마 썼어?"), labelsOf(chips));
    }

    @Test
    @DisplayName("답이 하나만 말하므로 전체를 볼 통로를 첫 칩으로 준다")
    void putsBreakdownChipFirst() {
        List<Suggestion> chips = provider.suggest(spendingTurn(), "주거에 600,000원을 썼습니다.");

        assertEquals("전체 내역 볼래", labelsOf(chips).get(0));
    }

    @Test
    @DisplayName("전체 내역을 이미 보여준 턴에는 칩을 붙이지 않는다")
    void returnsNothingAfterBreakdown() {
        ChatContext ctx = spendingTurn();
        ctx.calledTools().add("getSpendingBreakdown");

        assertTrue(provider.suggest(ctx, "이번 달 834,650원 쓰셨어요.").isEmpty());
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
