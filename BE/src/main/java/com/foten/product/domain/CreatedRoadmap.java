package com.foten.product.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// POST /api/roadmap 계산 결과. RoadmapStatus 와 같은 위치 — DTO 단계에서 CreateRoadmapResponse 로 옮겨 담긴다.
public record CreatedRoadmap(
        int totalMonths,
        BigDecimal baselineAmount,
        BigDecimal requiredAmount,   // 이 시점엔 baselineAmount 와 같다 (아직 과거 실적이 없음)
        SegmentSummary segment
) {
    public record SegmentSummary(
            int segmentNo,
            int plannedMonths,
            LocalDate startDate,
            LocalDate endDate,
            boolean isLastSegment
    ) {
    }
}
