package com.foten.product.dto;

import com.foten.product.domain.RoadmapStatus;
import java.math.BigDecimal;

public record RoadmapStatusResponse(
        boolean roadmapExists,
        String flowType,                     // ONBOARDING / NEW_SEGMENT / REGULAR_MONTH
        Integer cycleNo,
        Integer currentSegmentNo,
        Boolean isLastSegment,
        Boolean pendingSegmentTransition,
        BigDecimal lastMonthActualAmount,
        Boolean hasShortfall,
        BigDecimal shortfallAmount,
        BigDecimal rolloverAmount,
        BigDecimal baselineAmount,
        BigDecimal requiredAmount
) {
    public static RoadmapStatusResponse from(RoadmapStatus status) {
        return new RoadmapStatusResponse(
                status.roadmapExists(),
                status.flowType(),
                status.cycleNo(),
                status.currentSegmentNo(),
                status.isLastSegment(),
                status.pendingSegmentTransition(),
                status.lastMonthActualAmount(),
                status.hasShortfall(),
                status.shortfallAmount(),
                status.rolloverAmount(),
                status.baselineAmount(),
                status.requiredAmount());
    }
}
