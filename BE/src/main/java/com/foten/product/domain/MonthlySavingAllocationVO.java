package com.foten.product.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// monthly_saving_allocation 테이블과 1:1 대응하는 VO — 매월 상품별 배분 제안
// (예상 적용금리 내림차순). 실제 납입이 아니라 제안 내역이다.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySavingAllocationVO {
    private Long monthlySavingAllocationId;
    private Long monthlySavingPlanId;
    private Long productSubscriptionId;
    private BigDecimal allocatedAmount; // 이 적금에 이번 달 넣도록 제안하는 금액
    private Integer allocationOrder;    // 배분 순서 (예상 적용금리 내림차순, 1부터)
}
