package com.foten.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.foten.spending.domain.CategoryTotal;
import com.foten.spending.domain.MonthlySpending;
import com.foten.spending.domain.SpendingLine;
import com.foten.spending.mapper.SpendingSummaryMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpendingQueryServiceImplTest {

    private static final long MEMBER_ID = 1L;

    @Mock
    private SpendingSummaryMapper spendingSummaryMapper;

    private SpendingQueryServiceImpl service() {
        return new SpendingQueryServiceImpl(spendingSummaryMapper);
    }

    private static SpendingLine 지출(String category, String expenseType, long amount) {
        return SpendingLine.builder().category(category).expenseType(expenseType).amount(BigDecimal.valueOf(amount)).build();
    }

    @Test
    void topCategories_FIXED와_VARIABLE을_구분없이_카테고리별로_합산한다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of(
                지출("식비", "VARIABLE", 30_000),
                지출("식비", "FIXED", 20_000), // 같은 카테고리라도 FIXED/VARIABLE 안 가리고 합산
                지출("교통", "VARIABLE", 10_000)));

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertEquals(
                List.of(new CategoryTotal("식비", BigDecimal.valueOf(50_000)), new CategoryTotal("교통", BigDecimal.valueOf(10_000))),
                result.topCategories());
    }

    @Test
    void topCategories_금액_내림차순으로_정렬된다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of(
                지출("교통", "VARIABLE", 10_000),
                지출("식비", "VARIABLE", 50_000),
                지출("쇼핑", "VARIABLE", 30_000)));

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertEquals(List.of("식비", "쇼핑", "교통"), result.topCategories().stream().map(CategoryTotal::category).toList());
    }

    @Test
    void topCategories_상위_3개까지만_반환한다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of(
                지출("식비", "VARIABLE", 50_000),
                지출("쇼핑", "VARIABLE", 40_000),
                지출("교통", "VARIABLE", 30_000),
                지출("통신", "FIXED", 20_000),
                지출("기타", "VARIABLE", 10_000)));

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertEquals(3, result.topCategories().size());
        assertEquals(List.of("식비", "쇼핑", "교통"), result.topCategories().stream().map(CategoryTotal::category).toList());
    }

    @Test
    void topCategories_소비_이력이_없으면_빈_리스트를_반환한다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of());

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertTrue(result.topCategories().isEmpty());
    }
}
