package com.foten.product.domain;

import java.math.BigDecimal;

// product + product_rate 조인 결과 — 특정 가입기간(termMonths)에 가입 가능한 예금 후보 하나.
// ProductRateCandidate(적금용)와 구조가 비슷하지만 monthlyPaymentLimit 대신 예금 판단에 쓰는
// minSubscriptionAmount 를 담는다 (로직 v3 §6-4 — 목돈이 이 금액 미만이면 예금 미가입).
public record DepositRateCandidate(
        Long productId,
        String productName,
        BigDecimal maxRate,
        BigDecimal baseRate,
        BigDecimal minSubscriptionAmount
) {
}
