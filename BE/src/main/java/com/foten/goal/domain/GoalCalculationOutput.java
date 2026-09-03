package com.foten.goal.domain;

import java.math.BigDecimal;

public record GoalCalculationOutput(
        BigDecimal targetBaselineAmount, //목표 기준액
        int remainingMonths // 남은 개월 수
) {
}
