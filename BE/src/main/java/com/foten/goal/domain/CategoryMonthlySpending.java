package com.foten.goal.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// transaction_history 집계 결과 (카테고리 x 월 단위) — 특정 테이블과 1:1 대응하는 VO 아님
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryMonthlySpending {
    private String category;
    private String spendingMonth;     // "yyyy-MM". DB 컬럼 별칭은 spending_month — year_month는 MySQL 예약어라 못 씀
    private BigDecimal amount;
}
