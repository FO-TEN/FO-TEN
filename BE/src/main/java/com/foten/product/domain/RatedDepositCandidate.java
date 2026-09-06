package com.foten.product.domain;

import java.math.BigDecimal;

// selectDeposit() 입력 — 예상 적용금리까지 계산된 예금 후보 1건. ProductAllocationCandidate(적금용)와
// 구조가 비슷하지만 monthlyPaymentLimit 대신 최소가입금액 판단 기준인 minSubscriptionAmount 를 담는다.
// 호출 전에 appliedRate 내림차순으로 정렬돼 있어야 한다.
public record RatedDepositCandidate(Long productId, BigDecimal appliedRate, BigDecimal minSubscriptionAmount) {
}
