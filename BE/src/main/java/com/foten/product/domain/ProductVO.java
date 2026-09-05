package com.foten.product.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// product 테이블과 1:1 대응하는 VO
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVO {
    private Long productId;
    private String productName;
    private String productType;               // DEPOSIT / SAVINGS
    private String installmentType;            // FREE / FIXED — SAVINGS 만
    private BigDecimal monthlyPaymentLimit;    // 적금 월 최대 납입한도 — SAVINGS 만
    private BigDecimal maxRate;                // 상품 최고금리 (연 %)
    private BigDecimal minSubscriptionAmount;  // 최소 가입 금액
    private String description;
}
