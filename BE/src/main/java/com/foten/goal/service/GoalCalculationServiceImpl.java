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

        // 남은 개월수가 0 이하면 나눗셈 자체가 불가능하므로 달성 불가로 처리
        boolean achievable = remainingMonths > 0;

        BigDecimal requiredMonthlySaving = achievable
                ? input.targetAmountKrw()
                  .subtract(input.currentSavings())
                  .divide(BigDecimal.valueOf(remainingMonths), RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new GoalCalculationOutput(requiredMonthlySaving, remainingMonths, achievable);
    }

    private int calcRemainingMonths(GoalCalculationInput input) {
        Period period = Period.between(input.calculationDate(), input.expectedReturnDate());
        return period.getYears() * 12 + period.getMonths();
    }
}