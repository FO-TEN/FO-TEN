package com.foten.goal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
// 목표저축액
// 환전은 exchange 도메인에서, 여기서는 이미 KRW로 환산된 금액만 받는다.
public record GoalCalculationInput(
        BigDecimal targetAmountKrw,
        BigDecimal currentSavings,
        LocalDate calculationDate,
        LocalDate expectedReturnDate
) {

    public GoalCalculationInput {
        if (targetAmountKrw == null || currentSavings == null
                || calculationDate == null || expectedReturnDate == null) {
            throw new IllegalArgumentException("모든 필드는 null일 수 없다");
        }
    }
}
