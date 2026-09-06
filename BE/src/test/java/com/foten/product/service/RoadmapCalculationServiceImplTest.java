package com.foten.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foten.product.domain.FirstSegmentPlan;
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

    @Test
    void calculateFirstSegment_총개월수가_12이하면_그대로_단일_마지막_구간이다() {
        FirstSegmentPlan plan = service.calculateFirstSegment(5);

        assertEquals(5, plan.plannedMonths());
        assertTrue(plan.isLastSegment());
    }

    @Test
    void calculateFirstSegment_총개월수가_12초과면_잔여와_상관없이_무조건_12개월로_시작한다() {
        // 로직 v3 §3-2 예시: 13개월 → 12+1 (13개월 통짜 1구간이 아니라 12로 먼저 끊는다)
        FirstSegmentPlan thirteen = service.calculateFirstSegment(13);
        // 57개월 → 12+12+12+21
        FirstSegmentPlan fiftySeven = service.calculateFirstSegment(57);

        assertEquals(12, thirteen.plannedMonths());
        assertFalse(thirteen.isLastSegment());
        assertEquals(12, fiftySeven.plannedMonths());
        assertFalse(fiftySeven.isLastSegment());
    }

    @Test
    void calculateSegmentEndDate_마지막_구간이면_어림개월수_대신_로드맵_종료일을_그대로_쓴다() {
        LocalDate roadmapEndDate = LocalDate.of(2027, 9, 5); // 어림 계산이면 startDate+12개월(2027-09-06)과 하루 어긋남
        LocalDate result = service.calculateSegmentEndDate(
                LocalDate.of(2026, 9, 6), 12, true, roadmapEndDate);

        assertEquals(roadmapEndDate, result);
    }

    @Test
    void calculateSegmentEndDate_마지막_구간이_아니면_시작일에_계획개월수를_더한다() {
        LocalDate result = service.calculateSegmentEndDate(
                LocalDate.of(2026, 9, 6), 12, false, LocalDate.of(2099, 1, 1));

        assertEquals(LocalDate.of(2027, 9, 6), result);
    }
}
