package com.foten.product.service;

import com.foten.product.domain.AllocationPlan;
import com.foten.product.domain.AllocationPlan.AllocationEntry;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.ProductAllocationCandidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoadmapCalculationServiceImpl implements RoadmapCalculationService {

    private static final int GENERAL_SEGMENT_MONTHS = 12;

    @Override
    public BigDecimal reverseTargetAmount(BigDecimal baselineAmount, int totalMonths, BigDecimal initialAccumulatedFund) {
        return baselineAmount.multiply(BigDecimal.valueOf(totalMonths)).add(initialAccumulatedFund);
    }

    @Override
    public BigDecimal calculateCurrentAccumulatedFund(
            BigDecimal depositPrincipal, BigDecimal segmentSavingsPaid, BigDecimal cashSavingBalance) {
        return depositPrincipal.add(segmentSavingsPaid).add(cashSavingBalance);
    }

    @Override
    public int calculateRemainingMonths(LocalDate today, LocalDate endDate) {
        long months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(endDate));
        return (int) Math.max(months, 1); // 0개월 개념을 쓰지 않음 — 최소 1개월
    }

    @Override
    public BigDecimal calculateRequiredAmount(BigDecimal targetAmount, BigDecimal currentAccumulatedFund, int remainingMonths) {
        return targetAmount.subtract(currentAccumulatedFund)
                .divide(BigDecimal.valueOf(remainingMonths), RoundingMode.HALF_UP);
    }

    @Override
    public int calculateCycleNo(LocalDate startDate, LocalDate today) {
        long monthsSinceStart = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(today));
        return (int) monthsSinceStart + 1;
    }

    @Override
    public BigDecimal calculateCumulativeSavingPerformance(BigDecimal savingsPaymentSum, BigDecimal lastCashSavingBalance) {
        return savingsPaymentSum.add(lastCashSavingBalance);
    }

    @Override
    public BigDecimal calculateShortfall(BigDecimal baselineAmount, int completedCycles, BigDecimal cumulativeSavingPerformance) {
        BigDecimal target = baselineAmount.multiply(BigDecimal.valueOf(completedCycles));
        return target.subtract(cumulativeSavingPerformance).max(BigDecimal.ZERO);
    }

    @Override
    public FirstSegmentPlan calculateFirstSegment(int totalMonths) {
        if (totalMonths <= GENERAL_SEGMENT_MONTHS) {
            return new FirstSegmentPlan(totalMonths, true);
        }
        return new FirstSegmentPlan(GENERAL_SEGMENT_MONTHS, false);
    }

    @Override
    public LocalDate calculateSegmentEndDate(
            LocalDate segmentStartDate, int plannedMonths, boolean isLastSegment, LocalDate roadmapEndDate) {
        return isLastSegment ? roadmapEndDate : segmentStartDate.plusMonths(plannedMonths);
    }

    @Override
    public BigDecimal calculateExpectedAppliedRate(BigDecimal maxRate, BigDecimal baseRate, BigDecimal bonusSum) {
        return maxRate.min(baseRate.add(bonusSum));
    }

    @Override
    public AllocationPlan allocate(List<ProductAllocationCandidate> candidates, BigDecimal targetAmount) {
        List<AllocationEntry> allocations = new ArrayList<>();
        BigDecimal remaining = targetAmount;
        int order = 1;

        for (ProductAllocationCandidate candidate : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal amount = remaining.min(candidate.monthlyPaymentLimit());
            if (amount.signum() > 0) {
                allocations.add(new AllocationEntry(candidate.productId(), amount, order++));
                remaining = remaining.subtract(amount);
            }
        }

        BigDecimal recommendedCashSaving = remaining.max(BigDecimal.ZERO);
        return new AllocationPlan(allocations, recommendedCashSaving);
    }
}
