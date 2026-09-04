package com.foten.goal.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// goal 테이블과 1:1 대응하는 VO
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {
    private Long goalId;
    private Long memberId;
    private BigDecimal targetAmount;             // 목표 금액 (본국 통화 기준)
    private String targetCurrency;               // 예: VND, NPR
    private BigDecimal targetBaselineAmount;     // 목표기준액 (고정 스냅샷, KRW)
    private BigDecimal monthlyRequiredSaving;    // 필요저축액 (유동, 배치 계산값, KRW)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
