package com.foten.product.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// monthly_saving_plan 테이블과 1:1 대응하는 VO — 매월 저축 제안 (회원당 매월 1행)
// "이번 달 이렇게 저축하세요" 제안이며 실제 실행 결과가 아니다. 그 달 계산에 쓴 값들을
// 얼려서(baseline/required snapshot 등) 함께 저장한다.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySavingPlanVO {
    private Long monthlySavingPlanId;
    private Long savingsRoadmapId;
    private Long segmentId;
    private LocalDate planMonth;                       // 해당 월 YYYY-MM-01
    private Integer cycleNo;                             // 서비스 시작 기준 회차 (1부터)
    private String deficitChoice;                        // FULL_RECOVERY / SPREAD / NONE
    private BigDecimal monthlySavingAmount;              // 당월 저축액
    private BigDecimal recommendedCashSaving;            // 추천 현금성 저축액
    private BigDecimal currentAccumulatedFund;           // 현재 누적자금 (얼림)
    private BigDecimal cumulativeSavingPerformance;      // 직전월까지 누적 저축실적 (얼림)
    private BigDecimal baselineSnapshot;                 // 목표기준액 스냅샷
    private BigDecimal requiredSnapshot;                 // 필요저축액 스냅샷
    private BigDecimal projectedTotalInterest;           // 전체 로드맵 예상 이자 (세전, nullable)
    private LocalDateTime createdAt;
}
