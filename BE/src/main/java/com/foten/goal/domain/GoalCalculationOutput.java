package com.foten.goal.domain;

import java.math.BigDecimal;

public record GoalCalculationOutput(
        BigDecimal requiredMonthlySaving,
        int remainingMonths,
        boolean achievable
) {
}
