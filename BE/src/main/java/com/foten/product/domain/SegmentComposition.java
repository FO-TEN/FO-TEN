package com.foten.product.domain;

import java.math.BigDecimal;
import java.util.List;

// "추천 조합 상세" 결과 — POST /api/rate-conditions/responses(4-4)와 GET .../composition(4-5)이 공유한다
// (API 명세서 4-4: "Response — 4-5와 동일 스키마를 재사용"). ONBOARDING 분기만 있는 지금은
// deposit/rolloverAmount(목돈 없음)와 expectedInterestTotal/achievementRate(이자식 미확정)가 항상 null.
public record SegmentComposition(
        BigDecimal monthlyBaseline,
        BigDecimal rolloverAmount,
        DepositSummary deposit,
        List<SavingsSummary> savings,
        BigDecimal recommendedCashSaving,
        BigDecimal expectedInterestTotal,
        BigDecimal achievementRate
) {
    public record DepositSummary(
            String productName,
            int termMonths,
            BigDecimal appliedRate,
            BigDecimal principal,
            BigDecimal maturityAmount,
            BigDecimal expectedInterest
    ) {
    }

    public record SavingsSummary(
            String productName,
            int termMonths,
            BigDecimal appliedRate,
            BigDecimal monthlyLimit,
            BigDecimal monthlyAllocated
    ) {
    }
}
