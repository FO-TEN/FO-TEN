package com.foten.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foten.product.domain.AllocationPlan;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.ProductAllocationCandidate;
import com.foten.product.domain.RatedDepositCandidate;
import com.foten.product.domain.SavingsPaymentRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    void calculateNextSegment_잔여가_24개월_이상이면_12개월을_더_떼어낸다() {
        // 로직 v3 §3-2 예시: 57개월 로드맵의 2·3구간 — 첫 12 떼고 남은 45, 다시 뗀 뒤 남은 33
        FirstSegmentPlan afterFirst = service.calculateNextSegment(45);
        FirstSegmentPlan afterSecond = service.calculateNextSegment(33);
        // 경계값: 정확히 24개월 남았으면 "24개월 이상"이라 계속 12로 끊는다(36개월 예시의 마지막 12)
        FirstSegmentPlan boundary = service.calculateNextSegment(24);

        assertEquals(12, afterFirst.plannedMonths());
        assertFalse(afterFirst.isLastSegment());
        assertEquals(12, afterSecond.plannedMonths());
        assertFalse(afterSecond.isLastSegment());
        assertEquals(12, boundary.plannedMonths());
        assertFalse(boundary.isLastSegment());
    }

    @Test
    void calculateNextSegment_잔여가_24개월_미만이면_전부_마지막_구간이다() {
        // 로직 v3 §3-2 예시: 57개월 로드맵의 4구간 — 12+12+12를 떼고 남은 21 <24 → 21 전체가 마지막
        // (12+9로 더 쪼개면 안 된다 — 이게 calculateFirstSegment 를 잘못 재사용했을 때 나던 버그다)
        FirstSegmentPlan fiftySeven = service.calculateNextSegment(21);
        // 22개월 예시(12+10)의 2구간
        FirstSegmentPlan twentyTwo = service.calculateNextSegment(10);
        // 경계값: 23개월은 24 미만이라 쪼개지 않는다
        FirstSegmentPlan boundary = service.calculateNextSegment(23);

        assertEquals(21, fiftySeven.plannedMonths());
        assertTrue(fiftySeven.isLastSegment());
        assertEquals(10, twentyTwo.plannedMonths());
        assertTrue(twentyTwo.isLastSegment());
        assertEquals(23, boundary.plannedMonths());
        assertTrue(boundary.isLastSegment());
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

    @Test
    void calculateExpectedAppliedRate_기본금리와_우대금리_합이_최고금리를_넘으면_최고금리로_자른다() {
        BigDecimal result = service.calculateExpectedAppliedRate(
                BigDecimal.valueOf(5.0), BigDecimal.valueOf(3.0), BigDecimal.valueOf(2.5));

        assertEquals(BigDecimal.valueOf(5.0), result);
    }

    @Test
    void calculateExpectedAppliedRate_합이_최고금리보다_낮으면_합_그대로다() {
        BigDecimal result = service.calculateExpectedAppliedRate(
                BigDecimal.valueOf(6.0), BigDecimal.valueOf(3.0), BigDecimal.valueOf(1.5));

        assertEquals(BigDecimal.valueOf(4.5), result);
    }

    @Test
    void allocate_로직v3_예시대로_정렬순서대로_한도까지_채우고_남은_금액은_없다() {
        // 로직 v3 §4-6 예시: 필요저축액 100만원, A 5.0%/한도60만, B 4.6%/한도50만, C 4.2%/한도100만
        // → A, B 만 가입하고 C는 가입하지 않는다. 최초 납입 A:60만, B:40만
        List<ProductAllocationCandidate> candidates = List.of(
                new ProductAllocationCandidate(1L, BigDecimal.valueOf(5.0), BigDecimal.valueOf(600_000)),
                new ProductAllocationCandidate(2L, BigDecimal.valueOf(4.6), BigDecimal.valueOf(500_000)),
                new ProductAllocationCandidate(3L, BigDecimal.valueOf(4.2), BigDecimal.valueOf(1_000_000)));

        AllocationPlan plan = service.allocate(candidates, BigDecimal.valueOf(1_000_000));

        assertEquals(2, plan.allocations().size());
        assertEquals(1L, plan.allocations().get(0).productId());
        assertEquals(BigDecimal.valueOf(600_000), plan.allocations().get(0).allocatedAmount());
        assertEquals(1, plan.allocations().get(0).allocationOrder());
        assertEquals(2L, plan.allocations().get(1).productId());
        assertEquals(BigDecimal.valueOf(400_000), plan.allocations().get(1).allocatedAmount());
        assertEquals(0, plan.recommendedCashSaving().signum());
    }

    @Test
    void allocate_로직v3_예시대로_한도를_다_채우고도_남으면_추천_현금성_저축액이다() {
        // 로직 v3 §5-3 예시: A 최대60만 / B 최대50만, 당월저축액 130만 → A:60만 B:50만 현금성 20만
        List<ProductAllocationCandidate> candidates = List.of(
                new ProductAllocationCandidate(1L, BigDecimal.valueOf(5.0), BigDecimal.valueOf(600_000)),
                new ProductAllocationCandidate(2L, BigDecimal.valueOf(4.6), BigDecimal.valueOf(500_000)));

        AllocationPlan plan = service.allocate(candidates, BigDecimal.valueOf(1_300_000));

        assertEquals(BigDecimal.valueOf(600_000), plan.allocations().get(0).allocatedAmount());
        assertEquals(BigDecimal.valueOf(500_000), plan.allocations().get(1).allocatedAmount());
        assertEquals(BigDecimal.valueOf(200_000), plan.recommendedCashSaving());
    }

    @Test
    void allocate_후보가_없으면_전액_추천_현금성_저축액이다() {
        AllocationPlan plan = service.allocate(List.of(), BigDecimal.valueOf(500_000));

        assertTrue(plan.allocations().isEmpty());
        assertEquals(BigDecimal.valueOf(500_000), plan.recommendedCashSaving());
    }

    @Test
    void calculateDepositInterest_원금_곱하기_금리_곱하기_예치개월_나누기_12이다() {
        // 600만원, 2.25%, 10개월 → 600만 × 2.25% × 10/12 = 112,500원
        BigDecimal result = service.calculateDepositInterest(
                BigDecimal.valueOf(6_000_000), BigDecimal.valueOf(2.25), 10);

        assertEquals(0, BigDecimal.valueOf(112_500).compareTo(result));
    }

    @Test
    void calculateSavingsInterest_선납이연법으로_회차별_잔여개월을_곱해_합산한다() {
        // lan01 시드와 동일한 예시: 매달 50만원씩 12회, 5.00%, 만기까지 잔여개월 12..1
        // = 50만 × 5% × (12+11+...+1)/12 = 50만 × 0.05 × 6.5 = 162,500원
        LocalDate maturityDate = LocalDate.of(2026, 9, 5);
        List<SavingsPaymentRecord> payments = new ArrayList<>();
        for (int cycle = 0; cycle < 12; cycle++) {
            payments.add(new SavingsPaymentRecord(
                    BigDecimal.valueOf(500_000),
                    LocalDateTime.of(2025, 9, 5, 10, 0).plusMonths(cycle)));
        }

        BigDecimal result = service.calculateSavingsInterest(payments, BigDecimal.valueOf(5.0), maturityDate);

        assertEquals(0, BigDecimal.valueOf(162_500).compareTo(result));
    }

    @Test
    void calculateAfterTaxInterest_이자소득세_15_4퍼센트를_뗀다() {
        BigDecimal result = service.calculateAfterTaxInterest(BigDecimal.valueOf(162_500));

        assertEquals(0, BigDecimal.valueOf(137_475).compareTo(result));
    }

    @Test
    void selectDeposit_정렬순서대로_최소가입금액을_넘는_첫_후보를_고른다() {
        List<RatedDepositCandidate> candidates = List.of(
                new RatedDepositCandidate(1L, BigDecimal.valueOf(3.0), BigDecimal.valueOf(1_000_000)),
                new RatedDepositCandidate(2L, BigDecimal.valueOf(2.25), BigDecimal.valueOf(1_000_000)));

        Optional<RatedDepositCandidate> result = service.selectDeposit(candidates, BigDecimal.valueOf(6_162_500));

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().productId());
    }

    @Test
    void selectDeposit_최고금리_후보가_최소가입금액_미달이면_다음_후보를_고른다() {
        List<RatedDepositCandidate> candidates = List.of(
                new RatedDepositCandidate(1L, BigDecimal.valueOf(3.0), BigDecimal.valueOf(10_000_000)),
                new RatedDepositCandidate(2L, BigDecimal.valueOf(2.25), BigDecimal.valueOf(1_000_000)));

        Optional<RatedDepositCandidate> result = service.selectDeposit(candidates, BigDecimal.valueOf(6_162_500));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().productId());
    }

    @Test
    void selectDeposit_모든_후보의_최소가입금액에_못_미치면_비어있다() {
        List<RatedDepositCandidate> candidates = List.of(
                new RatedDepositCandidate(1L, BigDecimal.valueOf(3.0), BigDecimal.valueOf(1_000_000)));

        Optional<RatedDepositCandidate> result = service.selectDeposit(candidates, BigDecimal.valueOf(500_000));

        assertFalse(result.isPresent());
    }
}
