package com.foten.product.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// product_rate 테이블과 1:1 대응하는 VO — 상품 가입기간 구간별 기본금리
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRateVO {
    private Long productRateId;
    private Long productId;
    private Integer minTerm;    // 구간 최소 개월 (이상)
    private Integer maxTerm;    // 구간 최대 개월 (이하). null = 상한 없음
    private BigDecimal baseRate; // 해당 구간 연 기본금리 (%)
}
