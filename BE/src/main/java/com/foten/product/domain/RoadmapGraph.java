package com.foten.product.domain;

import java.math.BigDecimal;
import java.util.List;

// GET /api/roadmap/graph(4-7) 결과. 과거·현재 구간은 실제 데이터, 미래 구간은 "지금 조건이
// 계속 유지된다"는 가정으로 재귀 시뮬레이션한 값이다(로직 v3 §11 — 미래 금리는 예측하지 않는다).
public record RoadmapGraph(
        int totalMonths,
        List<SegmentSummary> segments,
        BigDecimal finalAmount,          // Σ savingsAmount — 실제로 낸/낼 원금 총합
        BigDecimal expectedInterestTotal // 마지막 구간 (savingsAmount+depositAmount+interestAmount) − finalAmount
) {
    public record SegmentSummary(
            int segmentNo,
            int months,
            String status,               // COMPLETED / ACTIVE / FUTURE
            BigDecimal savingsAmount,
            BigDecimal depositAmount,
            BigDecimal cashAmount,
            BigDecimal interestAmount
    ) {
    }
}
