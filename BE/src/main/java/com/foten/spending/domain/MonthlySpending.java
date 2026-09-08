package com.foten.spending.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

// 한 달 소비 집계
public record MonthlySpending(
        YearMonth month,
        int daysCovered,    // 이번 달이면 오늘 날짜, 지난 달이면 그 달 일수
        BigDecimal fixedTotal,
        Map<String, BigDecimal> fixedByCategory,
        BigDecimal variableTotal,
        Map<String, BigDecimal> variableByCategory,
        List<CategoryTotal> categoryTotals // FIXED+VARIABLE 합산 카테고리별 전체, 금액 내림차순 (홈·소비내역 화면 전용)
) {
    public BigDecimal total() {
        return fixedTotal.add(variableTotal);
    }
}
