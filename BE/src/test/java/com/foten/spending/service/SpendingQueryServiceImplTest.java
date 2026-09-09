package com.foten.spending.service;

import com.foten.common.clock.DemoClock;
import com.foten.common.mapper.DemoClockMapper;
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

    // demo_clock 행이 없으면(기본 Optional.empty) 실제 오늘을 쓴다 — 기존 테스트 전제 그대로.
    @Mock private DemoClockMapper demoClockMapper;

    private static final long MEMBER_ID = 1L;

    @Mock
    private SpendingSummaryMapper spendingSummaryMapper;

    private SpendingQueryServiceImpl service() {
        return new SpendingQueryServiceImpl(spendingSummaryMapper, new DemoClock(demoClockMapper));
    }

    private static SpendingLine 지출(String category, String expenseType, long amount) {
        return SpendingLine.builder().category(category).expenseType(expenseType).amount(BigDecimal.valueOf(amount)).build();
    }

    @Test
    void categoryTotals_FIXED와_VARIABLE을_구분없이_카테고리별로_합산한다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of(
                지출("식비", "VARIABLE", 30_000),
                지출("식비", "FIXED", 20_000), // 같은 카테고리라도 FIXED/VARIABLE 안 가리고 합산
                지출("교통", "VARIABLE", 10_000)));

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertEquals(
                List.of(new CategoryTotal("식비", BigDecimal.valueOf(50_000)), new CategoryTotal("교통", BigDecimal.valueOf(10_000))),
                result.categoryTotals());
    }

    @Test
    void categoryTotals_금액_내림차순으로_정렬된다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of(
                지출("교통", "VARIABLE", 10_000),
                지출("식비", "VARIABLE", 50_000),
                지출("쇼핑", "VARIABLE", 30_000)));

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertEquals(List.of("식비", "쇼핑", "교통"), result.categoryTotals().stream().map(CategoryTotal::category).toList());
    }

    @Test
    void categoryTotals_카테고리가_5개여도_전부_반환한다() {
        // 홈 화면은 이 중 앞 3개만 잘라 쓰지만, 서비스 레이어 자체는 자르지 않는다 — 소비내역 화면이 전체를 써야 하므로.
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of(
                지출("식비", "VARIABLE", 50_000),
                지출("쇼핑", "VARIABLE", 40_000),
                지출("교통", "VARIABLE", 30_000),
                지출("통신", "FIXED", 20_000),
                지출("기타", "VARIABLE", 10_000)));

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertEquals(5, result.categoryTotals().size());
        assertEquals(
                List.of("식비", "쇼핑", "교통", "통신", "기타"),
                result.categoryTotals().stream().map(CategoryTotal::category).toList());
    }

    @Test
    void categoryTotals_소비_이력이_없으면_빈_리스트를_반환한다() {
        when(spendingSummaryMapper.findByMonth(MEMBER_ID, 0)).thenReturn(List.of());

        MonthlySpending result = service().getMonthlySpending(MEMBER_ID, 0);

        assertTrue(result.categoryTotals().isEmpty());
    }
}
