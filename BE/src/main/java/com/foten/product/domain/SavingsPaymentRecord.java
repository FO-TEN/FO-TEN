package com.foten.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 적금 구독 하나의 회차별 실제 납입 내역 (transaction_history 의 SAVINGS_PAYMENT 원본).
// 만기 이자 "선납이연법"(이자_계산식_결정.md) 계산에 회차별 납입일이 그대로 필요해서
// 합계가 아니라 건별로 가져온다.
public record SavingsPaymentRecord(BigDecimal amount, LocalDateTime transactionAt) {
}
