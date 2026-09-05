package com.foten.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OnboardingResponse(
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal targetAmountKrw,
        BigDecimal targetBaselineAmount,
        int remainingMonths,
        BigDecimal exchangeRate,
        LocalDate exchangeRateBaseDate
) {
}