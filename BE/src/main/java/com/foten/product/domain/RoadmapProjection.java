package com.foten.product.domain;

import java.math.BigDecimal;

// "전체 로드맵 투영" 결과 — 4-4/4-5의 SegmentComposition.expectedInterestTotal/achievementRate,
// monthly_saving_plan.projected_total_interest 가 전부 이 값을 공유한다.
// achievementRate(%) = (원금 합계(finalAmount) + 예상 이자(expectedInterestTotal)) ÷ 목표저축액(KRW) × 100
public record RoadmapProjection(BigDecimal expectedInterestTotal, BigDecimal achievementRate) {
}
