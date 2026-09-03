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
    private String yearMonth;     // "yyyy-MM"
    private BigDecimal amount;
}
