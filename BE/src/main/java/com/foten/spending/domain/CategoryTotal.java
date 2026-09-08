package com.foten.spending.domain;

import java.math.BigDecimal;

// 카테고리별 합산 소비액 — FIXED/VARIABLE 구분 없이 합친 값 (topCategories 전용)
public record CategoryTotal(String category, BigDecimal amount) {
}
