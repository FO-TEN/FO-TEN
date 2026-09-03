package com.foten.goal.dto;

import java.math.BigDecimal;

public record GoalDiagnosisResponse(
        BigDecimal monthlyBaseline,      // 목표기준액 (고정) = goal.target_baseline_amount
        BigDecimal monthlyRequired,      // 필요저축액 (유동) = goal.monthly_required_saving
        BigDecimal additionalNeeded,     // 이번 달 추가 필요액 (단월). 음수면 여유있음
        BigDecimal cumulativeShortfall,  // 누적 부족액 = 목표기준액x경과개월 - 실제누적저축액 (goal 생성일 기준)
        BigDecimal achievementRate,      // 달성률(%) = 실제누적저축액 / (목표기준액x경과개월) x 100
        String catchUpMode,              // LUMP_SUM / SPREAD — TODO: 담당자 미정, 로직 없음, null
        String judgeResult               // "불가능" | "노력하면 가능" | "여유있음"
) {
}
