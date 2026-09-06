package com.foten.product.domain;

import java.math.BigDecimal;

// product + product_rate 조인 결과 — 특정 가입기간(termMonths)에 가입 가능한 상품 후보 하나.
// 테이블 1:1 VO 가 아니라 상품 추천 계산 전용 조회 결과 타입이다.
public record ProductRateCandidate(
        Long productId,
        String productName,
        BigDecimal maxRate,
        BigDecimal monthlyPaymentLimit,
        BigDecimal baseRate
) {
}
