package com.foten.product.domain;

import java.math.BigDecimal;

// GET /api/roadmap/status 계산 결과. product 도메인 내부 계산 결과 타입이며
// (GoalCalculationOutput 과 같은 위치) DTO 단계에서 RoadmapStatusResponse 로 그대로 옮겨 담긴다.
public record RoadmapStatus(
        boolean roadmapExists,
        String flowType,                     // ONBOARDING / NEW_SEGMENT / REGULAR_MONTH — roadmapExists=false 면 null
        Integer cycleNo,
        Integer currentSegmentNo,
        Boolean isLastSegment,
        Boolean pendingSegmentTransition,
        BigDecimal lastMonthActualAmount,    // 지난달 실제 적금 납입액 — 마감된 달이 없으면 null
        Boolean hasShortfall,
        BigDecimal shortfallAmount,          // hasShortfall=false 면 null
        BigDecimal rolloverAmount,           // 구간 전환 만기 처리 로직 전까지 항상 null (추후 채움)
        BigDecimal baselineAmount,           // 목표기준액 (goal.target_baseline_amount, 읽기 전용)
        BigDecimal requiredAmount            // 필요저축액 — 매번 새로 계산, goal 컬럼은 안 읽음
) {
    public static RoadmapStatus notOnboarded() {
        return new RoadmapStatus(false, null, null, null, null, null, null, null, null, null, null, null);
    }

    // 로드맵은 생성됐지만 아직 우대조건 응답·첫 상품가입이 안 된 시점 ("서비스 최초 월").
    // 이 시점엔 목표기준액과 필요저축액이 같다 (로직 v3 §2-3 "최초 시점에는 동일할 수 있음").
    public static RoadmapStatus onboarding(Integer currentSegmentNo, Boolean isLastSegment, BigDecimal baselineAmount) {
        return new RoadmapStatus(
                true, "ONBOARDING", 1, currentSegmentNo, isLastSegment,
                false, null, false, null, null, baselineAmount, baselineAmount);
    }
}
