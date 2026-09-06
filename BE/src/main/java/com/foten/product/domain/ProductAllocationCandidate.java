package com.foten.product.domain;

import java.math.BigDecimal;

// 배분 계산(RoadmapCalculationService.allocate) 입력 — 예상 적용금리까지 계산된 후보 상품 1건.
// 호출 전에 appliedRate 내림차순으로 정렬돼 있어야 한다.
public record ProductAllocationCandidate(Long productId, BigDecimal appliedRate, BigDecimal monthlyPaymentLimit) {
}
