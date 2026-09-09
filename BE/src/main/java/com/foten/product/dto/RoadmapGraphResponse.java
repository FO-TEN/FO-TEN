package com.foten.product.dto;

import java.time.LocalDate;
import com.foten.product.domain.RoadmapGraph;
import java.math.BigDecimal;
import java.util.List;

public record RoadmapGraphResponse(
        int totalMonths,
        List<SegmentResponse> segments,
        BigDecimal finalAmount,
        BigDecimal expectedInterestTotal
        , LocalDate latestPlanMonth   // 가장 최근 확정 회차의 달 — 이번 달을 아직 확정하지 않았으면 지난달
) {
    public record SegmentResponse(
            int segmentNo,
            int months,
            String status,
            BigDecimal savingsAmount,
            BigDecimal depositAmount,
            BigDecimal cashAmount,
            BigDecimal interestAmount
    ) {
    }

    public static RoadmapGraphResponse from(RoadmapGraph graph) {
        List<SegmentResponse> segments = graph.segments().stream()
                .map(s -> new SegmentResponse(
                        s.segmentNo(), s.months(), s.status(),
                        s.savingsAmount(), s.depositAmount(), s.cashAmount(), s.interestAmount()))
                .toList();
        return new RoadmapGraphResponse(graph.totalMonths(), segments, graph.finalAmount(), graph.expectedInterestTotal(), graph.latestPlanMonth());
    }
}
