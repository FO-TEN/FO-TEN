package com.foten.product.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// GET /api/roadmap/monthly-plan/current(4-9) 결과 — 이번 달에 이미 확정된 배분을 그대로
// 보여주기만 한다(새 계산 없음). 실제 납입 여부는 구분하지 않는다 — 전부 "이번 달 제안".
public record MonthlyPlanSummary(
        LocalDate planMonth,
        BigDecimal monthlySavingAmount,
        List<AllocationSummary> allocations,
        BigDecimal recommendedCashSaving,
        BigDecimal totalAmount
) {
    public record AllocationSummary(String productName, BigDecimal appliedRate, BigDecimal allocatedAmount, BigDecimal monthlyLimit) {
    }
}
