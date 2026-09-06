package com.foten.product.domain;

// RoadmapCalculationService.calculateFirstSegment() 결과 (로직 v3 §3-2, 최초 구간 분해).
public record FirstSegmentPlan(int plannedMonths, boolean isLastSegment) {
}
