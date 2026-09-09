package com.foten.product.domain;

// RoadmapCalculationService.calculateFirstSegment()/calculateNextSegment() 결과 (로직 v3 §3-2, 구간 분해).
// 두 메서드가 임계값만 다르고(12 vs 24) 반환 모양이 같아 레코드를 공유한다.
public record FirstSegmentPlan(int plannedMonths, boolean isLastSegment) {
}
