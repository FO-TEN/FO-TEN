package com.foten.product.service;

import com.foten.product.domain.AllocationPlan;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.ProductAllocationCandidate;
import com.foten.product.domain.RatedDepositCandidate;
import com.foten.product.domain.SavingsPaymentRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    // 최초 구간(1번째 구간) 전용 분해 (§3-2). 총 개월수 12 이하면 그 값 그대로 단일(=마지막) 구간,
    // 초과하면 잔여와 상관없이 무조건 12개월로 시작한다("첫 12개월 구간을 생성한 뒤").
    // 2번째 구간부터는 기준이 다르므로(잔여 24개월) calculateNextSegment 를 쓴다 — 이 메서드를
    // 재사용하면 안 된다(FO-TEN 마지막 구간 21개월이 12+9로 잘못 쪼개지는 버그의 원인이었다).
    FirstSegmentPlan calculateFirstSegment(int totalMonths);

    // 2번째 구간부터의 분해 (§3-2). "잔여 개월이 24개월 이상인 동안 12개월 구간을 추가하고,
    // 잔여가 24개월 미만이 되면 그 전부를 마지막 구간으로 지정" — calculateFirstSegment 와
    // 임계값이 다르다(12가 아니라 24). 예: 57개월 로드맵의 2구간 계산 시 잔여21 → 21<24라
    // 더 쪼개지 않고 21 전체가 마지막 구간(12+12+12+21).
    FirstSegmentPlan calculateNextSegment(int remainingMonths);

    // 구간 종료일. 마지막 구간이면 total_months 어림 계산으로 생긴 며칠 오차를 없애기 위해
    // roadmapEndDate 를 그대로 쓰고, 아니면 시작일 + 계획개월수로 계산한다.
    LocalDate calculateSegmentEndDate(
            LocalDate segmentStartDate, int plannedMonths, boolean isLastSegment, LocalDate roadmapEndDate);

    // 개인별 예상 적용금리 = MIN(상품 최고금리, 기본금리 + Σ 향후 충족 예정 우대금리) (§4-4)
    BigDecimal calculateExpectedAppliedRate(BigDecimal maxRate, BigDecimal baseRate, BigDecimal bonusSum);

    // 적금 월 납입액 배분 (§4-6/§5-3). candidates 는 호출 전 appliedRate 내림차순 정렬이 전제다.
    // 정렬 순서대로 상품별 월 한도까지 채우고, 다 채우고도 남으면 추천 현금성 저축액으로 돌린다.
    // "마지막 구간이라 상품을 1개만 쓴다"는 규칙은 없다 — 후보 자체가 그 구간 기간을 커버하는
    // 상품만 걸러져 나온 결과일 뿐이라 이 메서드는 구간 종류를 몰라도 된다.
    AllocationPlan allocate(List<ProductAllocationCandidate> candidates, BigDecimal targetAmount);

    // 예금 만기 이자 = 원금 × 연이율 × (예치개월수 ÷ 12) (이자_계산식_결정.md)
    BigDecimal calculateDepositInterest(BigDecimal principal, BigDecimal rate, int termMonths);

    // 적금 만기 이자 "선납이연법" = Σ(각 회차 납입액 × 연이율 × (그 납입월부터 만기월까지 남은 개월수 ÷ 12))
    // (이자_계산식_결정.md). 잔여개월은 납입일·만기일의 달력월 차이로 셈한다(일 단위 아님).
    BigDecimal calculateSavingsInterest(List<SavingsPaymentRecord> payments, BigDecimal rate, LocalDate maturityDate);

    // 이자소득세(15.4%) 원천징수 후 금액 — DB엔 항상 세전 저장, API 응답에 보여줄 때만 이 값을 쓴다.
    BigDecimal calculateAfterTaxInterest(BigDecimal preTaxInterest);

    // 새 구간에 개설할 예금 선택 (§6-2~6-4) — candidates 는 appliedRate 내림차순 정렬이 전제.
    // 정렬 순서대로 훑어 minSubscriptionAmount ≤ lumpSum 인 첫 번째(=최고금리 중 가입 가능한 것)를
    // 고른다. 목돈이 모든 후보의 최소가입금액에 못 미치면(또는 후보가 없으면) empty — 이 경우
    // 예금을 열지 않고 목돈 전액을 현금성으로 이월한다.
    Optional<RatedDepositCandidate> selectDeposit(List<RatedDepositCandidate> candidates, BigDecimal lumpSum);
}
