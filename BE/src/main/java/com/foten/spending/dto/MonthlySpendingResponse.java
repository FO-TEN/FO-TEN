package com.foten.spending.dto;

import com.foten.spending.domain.CategoryTotal;
import com.foten.spending.domain.MonthlySpending;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MonthlySpendingResponse(
        String month,
        int daysCovered,
        BigDecimal total,
        BigDecimal fixedTotal,
        Map<String, BigDecimal> fixedByCategory,
        BigDecimal variableTotal,
        Map<String, BigDecimal> variableByCategory,
        List<CategoryTotal> categoryTotals
) {
    public static MonthlySpendingResponse from(MonthlySpending s) {
        return new MonthlySpendingResponse(
                s.month().toString(), s.daysCovered(), s.total(),
                s.fixedTotal(), s.fixedByCategory(),
                s.variableTotal(), s.variableByCategory(),
                s.categoryTotals()
        );
    }
}
