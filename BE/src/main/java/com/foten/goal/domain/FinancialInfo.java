package com.foten.goal.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// financial_info 테이블과 1:1 대응하는 VO
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialInfo {
    private Long memberId;
    private BigDecimal monthlyIncome;        // 월 소득
    private BigDecimal monthlyLivingCost;    // 월 고정 생활비 (3a/3b 계산의 고정비 기준값)
    private BigDecimal monthlyRemittance;    // 월 정기 송금액
    private BigDecimal currentSavings;       // 현재까지 모은 금액 (KRW)
    private LocalDateTime updatedAt;
}
