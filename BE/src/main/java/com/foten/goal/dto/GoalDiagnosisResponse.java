package com.foten.goal.dto;

import com.foten.goal.domain.CategorySavingPotential;
import java.math.BigDecimal;
import java.util.List;

public record GoalDiagnosisResponse(
        BigDecimal monthlyBaseline,      // 목표기준액 (고정) = goal.target_baseline_amount
        BigDecimal monthlyRequired,      // 필요저축액 (유동) = goal.monthly_required_saving
        BigDecimal additionalNeeded,     // 이번 달 추가 필요액 (단월). 음수면 여유있음
        BigDecimal cumulativeShortfall,  // 누적 부족액 = 목표기준액x경과개월 - 실제누적저축액 (goal 생성일 기준)
        BigDecimal achievementRate,      // 달성률(%) = 실제누적저축액 / (목표기준액x경과개월) x 100
        String deficitChoice,            // FULL_RECOVERY / SPREAD / NONE - 값 채우는 로직은 아직 없어 null.
        String judgeResult,              // "불가능" | "노력하면 가능" | "여유있음"
        BigDecimal currentExpectedSaving, // 현재예상저축액
        BigDecimal maxExpectedSaving,     // 최대예상저축액
        String topSavingCategory,         // 절감 여력 1위 카테고리
        BigDecimal topSavingAmount,       // 그 카테고리의 절감 가능액
        int elapsedMonths,                // 경과 개월수 (goal 생성일 기준, 최소 1)
        BigDecimal cumulativeTarget,       // 누적 목표액 = 목표기준액 x 경과개월
        BigDecimal actualCumulativeSavings, // 실제 누적 저축액
        List<CategorySavingPotential> savingByCategory // 항목별 절감 여력 (0 초과만, 내림차순)
) {
}
