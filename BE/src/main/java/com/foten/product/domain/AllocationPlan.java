package com.foten.product.domain;

import java.math.BigDecimal;
import java.util.List;

// RoadmapCalculationService.allocate() 결과 (로직 v3 §4-6/§5-3).
public record AllocationPlan(List<AllocationEntry> allocations, BigDecimal recommendedCashSaving) {
    public record AllocationEntry(Long productId, BigDecimal allocatedAmount, int allocationOrder) {
    }
}
