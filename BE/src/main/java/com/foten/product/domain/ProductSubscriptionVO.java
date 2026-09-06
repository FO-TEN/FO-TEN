package com.foten.product.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// product_subscription 테이블과 1:1 대응하는 VO — 사용자의 상품 가입 인스턴스
// 가입 시점 예상 적용금리·월 납입한도를 스냅샷으로 박아 이후 product_rate 가 바뀌어도
// 구간 내내 이 값으로 배분·정렬한다 (로직 v3 §5-1, §5-3).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSubscriptionVO {
    private Long productSubscriptionId;
    private Long memberId;
    private Long productId;
    private Long segmentId;
    private String subscriptionRole;               // NEW_SAVINGS / ROLLOVER_DEPOSIT
    private Integer termMonths;                     // 실제 가입 개월
    private LocalDate startDate;
    private LocalDate maturityDate;
    private BigDecimal expectedAppliedRate;         // 가입 시점 예상 적용금리 스냅샷 (%)
    private BigDecimal monthlyPaymentLimitSnapshot;  // 가입 시점 월 납입한도 스냅샷 — 적금만
    private BigDecimal initialPrincipal;             // 예치 원금 — 예금만
    private String status;                          // ACTIVE / MATURED
    private BigDecimal maturityAmount;               // 만기 확정금 (원금+이자 합산)
    private LocalDateTime createdAt;
}
