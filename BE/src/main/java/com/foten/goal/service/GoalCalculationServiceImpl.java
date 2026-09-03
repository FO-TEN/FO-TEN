package com.foten.goal.service;

import com.foten.goal.domain.GoalCalculationInput;
import com.foten.goal.domain.GoalCalculationOutput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Period;

public class GoalCalculationServiceImpl implements GoalCalculationService {

    @Override
    public GoalCalculationOutput calculate(GoalCalculationInput input) {
        int remainingMonths = calcRemainingMonths(input);

        BigDecimal targetBaselineAmount = input.targetAmountKrw()
                .subtract(input.currentSavings())
                .divide(BigDecimal.valueOf(remainingMonths), RoundingMode.HALF_UP);

        return new GoalCalculationOutput(targetBaselineAmount, remainingMonths);
    }

    private int calcRemainingMonths(GoalCalculationInput input) {
        Period period = Period.between(input.calculationDate(), input.expectedReturnDate());
        int totalMonths = period.getYears() * 12 + period.getMonths();
        int remainingMonths = totalMonths - 2; // 가입월, 해제월은 계산에서 제외
        return Math.max(remainingMonths, 1); // 0개월 개념을 쓰지 않음 — 최소 1개월
    }
}