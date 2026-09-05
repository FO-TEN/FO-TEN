package com.foten.spending.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingLine {
    private String category;    // 식비, 교통, 통신, 쇼핑, 기타
    private String expenseType; // FIXED, VARIABLE
    private BigDecimal amount;
}
