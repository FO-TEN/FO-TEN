package com.foten.product.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class RoadmapCalculationServiceImpl implements RoadmapCalculationService {

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
}
