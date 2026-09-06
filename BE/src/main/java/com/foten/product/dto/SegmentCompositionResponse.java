package com.foten.product.dto;

import com.foten.product.domain.SegmentComposition;
import java.math.BigDecimal;
import java.util.List;

// "추천 조합 상세" 응답. POST /api/rate-conditions/responses(4-4)와 GET .../composition(4-5)이
// 공유하는 응답 스키마 — 엔드포인트가 아니라 개념(SegmentComposition) 기준으로 이름을 지었다.
public record SegmentCompositionResponse(
        BigDecimal monthlyBaseline,
        BigDecimal rolloverAmount,
        DepositResponse deposit,
        List<SavingsResponse> savings,
        BigDecimal recommendedCashSaving,
        BigDecimal expectedInterestTotal,
        BigDecimal achievementRate
) {
    public record DepositResponse(
            String productName,
            int termMonths,
            BigDecimal appliedRate,
            BigDecimal principal,
            BigDecimal maturityAmount,
            BigDecimal expectedInterest
    ) {
    }

    public record SavingsResponse(
            String productName,
            int termMonths,
            BigDecimal appliedRate,
            BigDecimal monthlyLimit,
            BigDecimal monthlyAllocated
    ) {
    }

    public static SegmentCompositionResponse from(SegmentComposition composition) {
        DepositResponse depositResponse = composition.deposit() == null ? null : new DepositResponse(
                composition.deposit().productName(),
                composition.deposit().termMonths(),
                composition.deposit().appliedRate(),
                composition.deposit().principal(),
                composition.deposit().maturityAmount(),
                composition.deposit().expectedInterest());

        List<SavingsResponse> savingsResponses = composition.savings().stream()
                .map(s -> new SavingsResponse(
                        s.productName(), s.termMonths(), s.appliedRate(), s.monthlyLimit(), s.monthlyAllocated()))
                .toList();

        return new SegmentCompositionResponse(
                composition.monthlyBaseline(),
                composition.rolloverAmount(),
                depositResponse,
                savingsResponses,
                composition.recommendedCashSaving(),
                composition.expectedInterestTotal(),
                composition.achievementRate());
    }
}
