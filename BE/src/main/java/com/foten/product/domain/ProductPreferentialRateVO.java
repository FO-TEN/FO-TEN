package com.foten.product.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// product_preferential_rate 테이블과 1:1 대응하는 VO — 상품별 우대금리 항목
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPreferentialRateVO {
    private Long productPreferentialRateId;
    private Long productId;
    private String conditionCode;
    private BigDecimal rateBonus; // 조건 충족 시 가산 우대금리 (%p)
}
