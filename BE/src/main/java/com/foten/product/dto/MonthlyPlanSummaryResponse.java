package com.foten.product.dto;

import com.foten.product.domain.MonthlyPlanSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlyPlanSummaryResponse(
        LocalDate planMonth,
        BigDecimal monthlySavingAmount,
        List<AllocationResponse> allocations,
        BigDecimal recommendedCashSaving,
        BigDecimal totalAmount
) {
    public record AllocationResponse(String productName, BigDecimal appliedRate, BigDecimal allocatedAmount, BigDecimal monthlyLimit) {
    }

    public static MonthlyPlanSummaryResponse from(MonthlyPlanSummary summary) {
        List<AllocationResponse> allocations = summary.allocations().stream()
                .map(a -> new AllocationResponse(a.productName(), a.appliedRate(), a.allocatedAmount(), a.monthlyLimit()))
                .toList();
        return new MonthlyPlanSummaryResponse(
                summary.planMonth(), summary.monthlySavingAmount(), allocations,
                summary.recommendedCashSaving(), summary.totalAmount());
    }
}
