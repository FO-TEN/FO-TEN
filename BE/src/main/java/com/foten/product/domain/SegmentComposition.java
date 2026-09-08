package com.foten.product.domain;

import java.math.BigDecimal;
import java.util.List;

// "추천 조합 상세" 결과 — POST /api/rate-conditions/responses(4-4)와 GET .../composition(4-5)이 공유한다
// (API 명세서 4-4: "Response — 4-5와 동일 스키마를 재사용").
//
// deposit/rolloverAmount 는 ONBOARDING(첫 구간)에서만 null 이다 — 굴릴 목돈이 아직 없어서다.
// expectedInterestTotal/achievementRate 는 #89 부터 두 경로 모두 getProjection() 으로 채운다.
//
// 이자 기준이 둘로 갈려 있으니 주의한다. deposit 의 expectedInterest/maturityAmount 는 세후이고,
// expectedInterestTotal 은 그래프에서 오는 값이라 세전이다. 서로 더하거나 견주면 안 된다.
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
