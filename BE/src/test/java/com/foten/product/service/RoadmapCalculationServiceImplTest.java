package com.foten.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RoadmapCalculationServiceImplTest {

    private final RoadmapCalculationServiceImpl service = new RoadmapCalculationServiceImpl();

    @Test
    void reverseTargetAmount_목표기준액과_최초개월수_최초누적자금으로_목표저축액을_역산한다() {
        // 로직 v3 §2-3 예시: 목표저축액 5,700만원 / 최초누적자금 0원 / 57개월 → 목표기준액 100만원
        BigDecimal result = service.reverseTargetAmount(BigDecimal.valueOf(1_000_000), 57, BigDecimal.ZERO);

        assertEquals(BigDecimal.valueOf(57_000_000), result);
    }

    @Test
    void calculateRemainingMonths_월_단위로_차이를_구하고_0개월_대신_최소_1개월로_보정한다() {
        int sameMonth = service.calculateRemainingMonths(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 20));
        int twelveMonths = service.calculateRemainingMonths(LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15));

        assertEquals(1, sameMonth);
        assertEquals(12, twelveMonths);
    }

    @Test
    void calculateRequiredAmount_로직v3_예시_숫자와_일치한다() {
        // 로직 v3 §2-4 예시: (5,700만원 - 70만원) ÷ 56개월 ≈ 100.5만원
        BigDecimal result = service.calculateRequiredAmount(
                BigDecimal.valueOf(57_000_000), BigDecimal.valueOf(700_000), 56);

        assertEquals(BigDecimal.valueOf(1_005_357), result);
    }

    @Test
    void calculateCycleNo_로드맵_시작월_기준_경과개월_1을_더한_회차를_반환한다() {
        int firstMonth = service.calculateCycleNo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20));
        int sixthMonth = service.calculateCycleNo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));

        assertEquals(1, firstMonth);
        assertEquals(6, sixthMonth);
    }

    @Test
    void calculateShortfall_로직v3_예시대로_부족액_30만원을_구한다() {
        // 로직 v3 §2-6 예시: 목표기준액 100만원, 1개월 경과, 누적저축실적 70만원 → 부족 30만원
        BigDecimal result = service.calculateShortfall(
                BigDecimal.valueOf(1_000_000), 1, BigDecimal.valueOf(700_000));

        assertEquals(BigDecimal.valueOf(300_000), result);
    }

    @Test
    void calculateShortfall_계획보다_더_모았으면_음수_대신_0을_반환한다() {
        BigDecimal result = service.calculateShortfall(
                BigDecimal.valueOf(1_000_000), 1, BigDecimal.valueOf(1_200_000));

        assertEquals(0, result.signum());
    }
}
