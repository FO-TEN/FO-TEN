package com.foten.product.service;

import java.math.BigDecimal;
import java.time.LocalDate;

// 로드맵 도메인의 순수 계산 (DB 접근 없음, 단위테스트 대상). 로직 v3 §2 공식을 그대로 옮긴다.
public interface RoadmapCalculationService {

    // 목표저축액(KRW) 역산 = 목표기준액 × 최초 남은 저축 가능 개월수 + 최초 현재 누적자금.
    // 목표기준액 = (목표저축액 - 최초누적자금) / 최초개월수 (§2-3) 를 거꾸로 푼 식이라
    // exchange_rate 를 거치지 않고 이미 KRW로 고정된 baselineAmount 만으로 계산한다.
    BigDecimal reverseTargetAmount(BigDecimal baselineAmount, int totalMonths, BigDecimal initialAccumulatedFund);

    // 현재 누적자금 = 현재 예금 금액 + 현재 적금 실제 납입금액 + 실제 현금성 저축액 (§2-2)
    BigDecimal calculateCurrentAccumulatedFund(BigDecimal depositPrincipal, BigDecimal segmentSavingsPaid, BigDecimal cashSavingBalance);

    // 남은 저축 가능 개월수 = 오늘 ~ 로드맵 종료일 (§2-4 분모). 0개월 개념을 쓰지 않고 최소 1개월.
    int calculateRemainingMonths(LocalDate today, LocalDate endDate);

    // 필요저축액 = (목표저축액 - 현재 누적자금) ÷ 남은 저축 가능 개월수 (§2-4)
    BigDecimal calculateRequiredAmount(BigDecimal targetAmount, BigDecimal currentAccumulatedFund, int remainingMonths);

    // 현재 회차 = 로드맵 시작월 기준 오늘이 몇 번째 달인지 (1부터, §2-6 "현재 회차")
    int calculateCycleNo(LocalDate startDate, LocalDate today);

    // 누적 저축실적 = 로드맵 시작 이후 직전월까지 실제 적금 납입액 누계 + 직전월말 실제 현금성 저축액 (§2-5)
    BigDecimal calculateCumulativeSavingPerformance(BigDecimal savingsPaymentSum, BigDecimal lastCashSavingBalance);

    // 과거 부족액 = max(0, 목표기준액 × 이미 끝난 개월수 - 누적저축실적).
    // "이미 끝난 개월수"만 채점 대상이라 아직 결정 전인 이번 달(cycleNo)은 포함하지 않는다 —
    // 당월저축액 공식(§5-2, cycleNo 그대로 곱함)과는 보는 시점이 다르다.
    BigDecimal calculateShortfall(BigDecimal baselineAmount, int completedCycles, BigDecimal cumulativeSavingPerformance);
}
