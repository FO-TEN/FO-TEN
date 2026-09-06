package com.foten.product.dto;

import com.foten.product.domain.CreatedRoadmap;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRoadmapResponse(
        int totalMonths,
        BigDecimal baselineAmount,
        BigDecimal requiredAmount,
        SegmentResponse segment
) {
    public record SegmentResponse(
            int segmentNo,
            int plannedMonths,
            LocalDate startDate,
            LocalDate endDate,
            boolean isLastSegment
    ) {
    }

    public static CreateRoadmapResponse from(CreatedRoadmap roadmap) {
        CreatedRoadmap.SegmentSummary s = roadmap.segment();
        return new CreateRoadmapResponse(
                roadmap.totalMonths(),
                roadmap.baselineAmount(),
                roadmap.requiredAmount(),
                new SegmentResponse(s.segmentNo(), s.plannedMonths(), s.startDate(), s.endDate(), s.isLastSegment()));
    }
}
