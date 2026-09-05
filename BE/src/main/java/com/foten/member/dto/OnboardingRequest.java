package com.foten.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OnboardingRequest(
        LocalDate entryDate,
        LocalDate expectedReturnDate,
        BigDecimal monthlyIncome,
        BigDecimal monthlyLivingCost,
        BigDecimal monthlyRemittance,
        BigDecimal currentSavings,
        BigDecimal targetAmount,
        String targetCurrency
) {
}
