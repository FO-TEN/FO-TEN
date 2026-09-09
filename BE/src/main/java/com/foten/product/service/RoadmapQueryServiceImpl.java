package com.foten.product.service;

import com.foten.common.ResourceNotFoundException;
import com.foten.common.RoadmapStateConflictException;
import com.foten.goal.domain.Goal;
import com.foten.goal.mapper.GoalMapper;
import com.foten.product.domain.AllocationPlan;
import com.foten.product.domain.AssetSnapshotVO;
import com.foten.product.domain.DepositRateCandidate;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.MonthlySavingPlanVO;
import com.foten.product.domain.ProductAllocationCandidate;
import com.foten.product.domain.ProductPreferentialRateVO;
import com.foten.product.domain.ProductRateCandidate;
import com.foten.product.domain.ProductSubscriptionVO;
import com.foten.product.domain.ProductVO;
import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RatedDepositCandidate;
import com.foten.product.domain.RoadmapGraph;
import com.foten.product.domain.RoadmapProjection;
import com.foten.product.domain.RoadmapSegmentVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SavingsPaymentRecord;
import com.foten.product.domain.SavingsRoadmapVO;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.domain.SegmentDetail;
import com.foten.product.domain.MemberRateConditionResponseVO;
import com.foten.product.domain.MonthlyPlanSummary;
import com.foten.product.domain.MonthlySavingAllocationVO;
import com.foten.product.mapper.AssetSnapshotMapper;
import com.foten.product.mapper.MemberRateConditionResponseMapper;
import com.foten.product.mapper.MonthlySavingAllocationMapper;
import com.foten.product.mapper.MonthlySavingPlanMapper;
import com.foten.product.mapper.ProductMapper;
import com.foten.product.mapper.ProductPreferentialRateMapper;
import com.foten.product.mapper.ProductSubscriptionMapper;
import com.foten.product.mapper.RateConditionMapper;
import com.foten.product.mapper.RoadmapSegmentMapper;
import com.foten.product.mapper.SavingsRoadmapMapper;
import com.foten.product.mapper.TransactionHistoryMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoadmapQueryServiceImpl implements RoadmapQueryService {

    private static final String ROLLOVER_DEPOSIT = "ROLLOVER_DEPOSIT";
    private static final String NEW_SAVINGS = "NEW_SAVINGS";
    private static final String SPREAD = "SPREAD";
    private static final String COMPLETED = "COMPLETED";
    private static final String ACTIVE = "ACTIVE";
    private static final String FUTURE = "FUTURE";

    private final SavingsRoadmapMapper savingsRoadmapMapper;
    private final RoadmapSegmentMapper roadmapSegmentMapper;
    private final MonthlySavingPlanMapper monthlySavingPlanMapper;
    private final MonthlySavingAllocationMapper monthlySavingAllocationMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;
    private final ProductSubscriptionMapper productSubscriptionMapper;
    private final ProductMapper productMapper;
    private final ProductPreferentialRateMapper productPreferentialRateMapper;
    private final MemberRateConditionResponseMapper memberRateConditionResponseMapper;
    private final TransactionHistoryMapper transactionHistoryMapper;
    private final RateConditionMapper rateConditionMapper;
    private final GoalMapper goalMapper; // 교차 도메인, 읽기 전용 (target_baseline_amount 절대 안 씀)
    private final RoadmapCalculationService roadmapCalculationService;

    @Override
    public RoadmapStatus getStatus(long memberId) {
        // STEP 1. 로드맵 자체가 없으면 "서비스 최초 월" — 뒤 계산 다 필요 없이 바로 끝.
        Optional<SavingsRoadmapVO> roadmapOpt = savingsRoadmapMapper.selectByMemberId(memberId);
        if (roadmapOpt.isEmpty()) {
            return RoadmapStatus.notOnboarded();
        }
        SavingsRoadmapVO roadmap = roadmapOpt.get();

        // STEP 2. 현재 진행 중인 구간(ACTIVE) 조회. 로드맵이 있으면 항상 정확히 1건 있어야 하는
        // 불변조건이라(구간이 끝나면 COMPLETED로 바꾸고 다음 구간을 바로 INSERT), 없으면 데이터 정합성이
        // 깨진 것이므로 정상 에러 케이스가 아니라 예외로 바로 터뜨린다.
        RoadmapSegmentVO segment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));

        // 오늘이 이 구간의 만기일(end_date)을 지났으면 "새 운용구간 시작 월" — 아직 다음 구간이
        // INSERT되지 않은, 만기는 지났지만 전환 처리는 안 된 상태를 뜻한다.
        LocalDate today = LocalDate.now();
        boolean pendingSegmentTransition = today.isAfter(segment.getEndDate());

        // STEP 3. 목표기준액은 goal 도메인이 이미 고정해둔 값을 읽기만 한다 (설계 원칙 7 — 여기서
        // 계산도 저장도 하지 않는다). 로드맵이 있는데 goal이 없는 건 데이터 정합성 문제라 예외 처리.
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new ResourceNotFoundException("목표 정보가 없습니다."));

        // STEP 4. cycle_no=1 행이 아직 없다 = 로드맵은 만들었지만 우대조건 응답·첫 상품가입 전이라는
        // 뜻 — 이게 진짜 "서비스 최초 월"이다(STEP 1의 "로드맵 자체가 없음"과는 다른 순간). 이 시점엔
        // 아직 비교할 과거 실적이 없어서 목표기준액을 그대로 필요저축액으로 쓰고 끝낸다.
        Optional<MonthlySavingPlanVO> firstPlan = monthlySavingPlanMapper.selectFirst(roadmap.getSavingsRoadmapId());
        if (firstPlan.isEmpty()) {
            return RoadmapStatus.onboarding(segment.getSegmentNo(), segment.getIsLastSegment(), baselineAmount);
        }

        // 여기부터는 최소 한 달 이상 지난 "일반 월" 또는 "새 구간 시작 월" — 실제 실적을 반영해
        // 필요저축액·부족액을 다시 계산해야 한다.

        // 가장 최근 마감된 달의 스냅샷. cash_saving_balance는 두 계산(§2-2 현재누적자금, §2-5 누적저축실적)에
        // 공통으로 쓰여서 한 번만 조회해 재사용한다.
        Optional<AssetSnapshotVO> latestSnapshot = assetSnapshotMapper.selectLatest(roadmap.getSavingsRoadmapId());
        BigDecimal cashSavingBalance = latestSnapshot.map(AssetSnapshotVO::getCashSavingBalance).orElse(BigDecimal.ZERO);

        // STEP 5. §2-2 현재 누적자금 = 이 구간의 예금 원금 + 이 구간 적금 실제 납입액 + 실제 현금성 저축액.
        // "이 구간"으로 한정하는 이유: 지난 구간에서 모은 돈은 이미 예금으로 롤오버됐고, 새 구간의
        // 적금은 0부터 다시 쌓이기 때문에(구간마다 별개 계좌처럼 취급).
        BigDecimal depositPrincipal = sumActiveDepositPrincipal(segment.getSegmentId());
        BigDecimal segmentSavingsPaid = transactionHistoryMapper.sumSavingsPaymentBySegment(segment.getSegmentId());
        BigDecimal currentAccumulatedFund = roadmapCalculationService.calculateCurrentAccumulatedFund(
                depositPrincipal, segmentSavingsPaid, cashSavingBalance);

        // STEP 6. 목표저축액(KRW)은 환율 변환 없이 역산한다: 목표기준액×최초개월수+최초누적자금.
        // firstPlan(cycle_no=1)의 current_accumulated_fund가 바로 그 "최초누적자금" 스냅샷이다.
        // 이걸로 §2-4 필요저축액 = (목표저축액-현재누적자금)÷남은개월수 를 구한다.
        BigDecimal targetAmount = roadmapCalculationService.reverseTargetAmount(
                baselineAmount, roadmap.getTotalMonths(), firstPlan.get().getCurrentAccumulatedFund());
        int remainingMonths = roadmapCalculationService.calculateRemainingMonths(today, roadmap.getEndDate());
        BigDecimal requiredAmount = roadmapCalculationService.calculateRequiredAmount(
                targetAmount, currentAccumulatedFund, remainingMonths);

        // STEP 7. 현재 회차 — 로드맵 시작월부터 오늘까지 몇 번째 달인지(이번 달 포함, 1부터).
        int cycleNo = roadmapCalculationService.calculateCycleNo(roadmap.getStartDate(), today);

        // STEP 8. §2-5 누적 저축실적 = 로드맵 시작 ~ "직전월까지"(이번 달 제외) 적금 납입 누계
        // + 직전월말 현금성 저축액. until을 이번 달 1일로 잘라 이번 달 거래가 섞여 들어오지 않게 한다.
        LocalDateTime roadmapStart = roadmap.getStartDate().atStartOfDay();
        LocalDateTime thisMonthStart = YearMonth.from(today).atDay(1).atStartOfDay();
        BigDecimal savingsPaymentSum =
                transactionHistoryMapper.sumSavingsPaymentBetween(memberId, roadmapStart, thisMonthStart);
        BigDecimal cumulativeSavingPerformance =
                roadmapCalculationService.calculateCumulativeSavingPerformance(savingsPaymentSum, cashSavingBalance);

        // STEP 9. 부족액 판정 — 아직 결정 안 된 이번 달(cycleNo)은 채점 대상이 아니라 "이미 끝난
        // 개월수"(cycleNo-1)만 목표기준액에 곱해 비교한다. 첫 달 안에 상태를 다시 조회하는 경우처럼
        // completedCycles가 0 이하면 비교할 과거 자체가 없으니 부족액도 없다.
        int completedCycles = cycleNo - 1;
        BigDecimal shortfallAmount = completedCycles > 0
                ? roadmapCalculationService.calculateShortfall(baselineAmount, completedCycles, cumulativeSavingPerformance)
                : BigDecimal.ZERO;
        boolean hasShortfall = shortfallAmount.signum() > 0;

        // STEP 10. 결과 조립. rolloverAmount(지난 구간에서 모은 돈)는 구간이 끝나 전환을 기다리는
        // 달에만 미리 계산해 준다 — 대화가 채울 방식을 묻기 전에 "지난 구간에서 모은 돈"을 먼저
        // 알려줘야 해서다. 전환 커밋(RoadmapCommandServiceImpl STEP 3-A)과 같은 식이고 저장은 없다.
        String flowType = pendingSegmentTransition ? "NEW_SEGMENT" : "REGULAR_MONTH";
        BigDecimal lastMonthActualAmount = latestSnapshot.map(AssetSnapshotVO::getMonthlyPayment).orElse(null);
        BigDecimal rolloverAmount = pendingSegmentTransition
                ? previewRolloverAmount(segment.getSegmentId(), cashSavingBalance)
                : null;

        return new RoadmapStatus(
                true, flowType, cycleNo, segment.getSegmentNo(), segment.getIsLastSegment(),
                pendingSegmentTransition, lastMonthActualAmount, hasShortfall,
                hasShortfall ? shortfallAmount : null, rolloverAmount, baselineAmount, requiredAmount);
    }

    // 구간을 마감하면 손에 쥐게 될 목돈(§7-2) = 구독별 (원금 + 세후 만기 이자) 합 + 그 구간 마지막
    // 현금성 저축액. 전환 커밋과 같은 식이라 대화에서 말한 금액과 실제 예금 원금이 어긋나지 않는다.
    private BigDecimal previewRolloverAmount(long segmentId, BigDecimal cashSavingBalance) {
        BigDecimal lumpSum = BigDecimal.ZERO;
        for (ProductSubscriptionVO subscription : productSubscriptionMapper.selectActiveBySegment(segmentId)) {
            BigDecimal principal;
            BigDecimal preTaxInterest;
            if (ROLLOVER_DEPOSIT.equals(subscription.getSubscriptionRole())) {
                principal = subscription.getInitialPrincipal();
                preTaxInterest = roadmapCalculationService.calculateDepositInterest(
                        principal, subscription.getExpectedAppliedRate(), subscription.getTermMonths());
            } else {
                List<SavingsPaymentRecord> payments =
                        transactionHistoryMapper.selectSavingsPaymentsBySubscription(subscription.getProductSubscriptionId());
                principal = payments.stream().map(SavingsPaymentRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                preTaxInterest = roadmapCalculationService.calculateSavingsInterest(
                        payments, subscription.getExpectedAppliedRate(), subscription.getMaturityDate());
            }
            lumpSum = lumpSum.add(principal).add(roadmapCalculationService.calculateAfterTaxInterest(preTaxInterest));
        }
        return lumpSum.add(cashSavingBalance);
    }

    @Override
    public List<RateConditionVO> getRateConditions() {
        return rateConditionMapper.selectBehaviorBased();
    }

    @Override
    public SegmentComposition getCurrentComposition(long memberId) {
        // 이 엔드포인트는 온보딩/구간전환 커밋 이후에만 호출된다고 전제한다 — getStatus() 와 달리
        // "아직 로드맵이 없음"을 정상 케이스로 봐주지 않는다.
        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        RoadmapSegmentVO segment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new ResourceNotFoundException("목표 정보가 없습니다."));

        List<ProductSubscriptionVO> subscriptions = productSubscriptionMapper.selectActiveBySegment(segment.getSegmentId());
        ProductSubscriptionVO depositSubscription = subscriptions.stream()
                .filter(s -> ROLLOVER_DEPOSIT.equals(s.getSubscriptionRole()))
                .findFirst()
                .orElse(null);
        List<ProductSubscriptionVO> savingsSubscriptions = subscriptions.stream()
                .filter(s -> NEW_SAVINGS.equals(s.getSubscriptionRole()))
                .toList();

        // 상품구성 기준액 판단 (§4-7) — 이 구간 첫 회차의 deficitChoice로 목표기준액/필요저축액 중 선택.
        MonthlySavingPlanVO firstPlanOfSegment = monthlySavingPlanMapper.selectFirstBySegment(segment.getSegmentId())
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "PRODUCTS_NOT_SELECTED", "이 구간의 저축 제안이 아직 없습니다."));
        BigDecimal basisAmount = SPREAD.equals(firstPlanOfSegment.getDeficitChoice())
                ? firstPlanOfSegment.getRequiredSnapshot()
                : baselineAmount;

        Map<Long, ProductVO> productById = subscriptions.isEmpty()
                ? Map.of()
                : productMapper.selectByIds(subscriptions.stream().map(ProductSubscriptionVO::getProductId).toList())
                        .stream()
                        .collect(Collectors.toMap(ProductVO::getProductId, p -> p));

        // 배분은 저장된 값을 읽지 않고 이 기준액으로 다시 계산한다 — 이 카드는 "이번 달 실제"가
        // 아니라 "이 구간 동안 유지할 기준"을 보여줘야 하기 때문 (UI v5 "여기서 보이는 월 100만
        // 원은 이번 달 130만 원이 아니라 앞으로 유지할 월 저축기준이에요").
        List<ProductAllocationCandidate> candidates = savingsSubscriptions.stream()
                .map(s -> new ProductAllocationCandidate(
                        s.getProductId(), s.getExpectedAppliedRate(), s.getMonthlyPaymentLimitSnapshot()))
                .sorted(Comparator.comparing(ProductAllocationCandidate::appliedRate).reversed())
                .toList();
        AllocationPlan plan = roadmapCalculationService.allocate(candidates, basisAmount);
        Map<Long, BigDecimal> allocatedByProductId = plan.allocations().stream()
                .collect(Collectors.toMap(
                        AllocationPlan.AllocationEntry::productId, AllocationPlan.AllocationEntry::allocatedAmount));

        List<SegmentComposition.SavingsSummary> savingsSummaries = savingsSubscriptions.stream()
                .map(s -> new SegmentComposition.SavingsSummary(
                        productById.get(s.getProductId()).getProductName(),
                        s.getTermMonths(),
                        s.getExpectedAppliedRate(),
                        s.getMonthlyPaymentLimitSnapshot(),
                        allocatedByProductId.getOrDefault(s.getProductId(), BigDecimal.ZERO)))
                .toList();

        // 예금은 아직 만기 전이라 product_subscription.maturity_amount 는 항상 NULL(만기 시에만
        // 채워짐) — 4-4 NEW_SEGMENT 커밋 때와 똑같이 원금·스냅샷 금리로 예상 만기금을 직접
        // 계산한다. 세후 변환까지 포함(이자_계산식_결정.md).
        SegmentComposition.DepositSummary depositSummary = null;
        if (depositSubscription != null) {
            BigDecimal depositInterest = roadmapCalculationService.calculateDepositInterest(
                    depositSubscription.getInitialPrincipal(), depositSubscription.getExpectedAppliedRate(),
                    depositSubscription.getTermMonths());
            BigDecimal afterTaxInterest = roadmapCalculationService.calculateAfterTaxInterest(depositInterest);
            depositSummary = new SegmentComposition.DepositSummary(
                    productById.get(depositSubscription.getProductId()).getProductName(),
                    depositSubscription.getTermMonths(),
                    depositSubscription.getExpectedAppliedRate(),
                    depositSubscription.getInitialPrincipal(),
                    depositSubscription.getInitialPrincipal().add(afterTaxInterest),
                    afterTaxInterest);
        }
        BigDecimal rolloverAmount = depositSubscription == null ? null : depositSubscription.getInitialPrincipal();

        RoadmapProjection projection = getProjection(memberId);
        return new SegmentComposition(
                baselineAmount, rolloverAmount, depositSummary, savingsSummaries,
                plan.recommendedCashSaving(), projection.expectedInterestTotal(), projection.achievementRate());
    }

    // §2-2 현재 누적자금의 "현재 예금 금액" 항목 — 이 구간의 ACTIVE ROLLOVER_DEPOSIT 구독 원금 합
    private BigDecimal sumActiveDepositPrincipal(Long segmentId) {
        return productSubscriptionMapper.selectActiveBySegment(segmentId).stream()
                .filter(s -> ROLLOVER_DEPOSIT.equals(s.getSubscriptionRole()))
                .map(ProductSubscriptionVO::getInitialPrincipal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public RoadmapGraph getGraph(long memberId) {
        // 이 엔드포인트도 getCurrentComposition 처럼 "로드맵 없음"을 정상 케이스로 안 본다.
        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new ResourceNotFoundException("목표 정보가 없습니다."));

        // STEP 1. 실제로 시작된 구간(COMPLETED+ACTIVE) — 과거·현재는 전부 실제 데이터.
        List<RoadmapSegmentVO> realSegments = roadmapSegmentMapper.selectAllByRoadmapId(roadmap.getSavingsRoadmapId());
        RoadmapSegmentVO activeSegment = realSegments.stream()
                .filter(s -> ACTIVE.equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));

        // "구성 기준액"(§4-7) — 현재 구간에서 가장 최근에 커밋된 회차의 deficitChoice 기준.
        // SPREAD로 확정된 적이 있으면 그 필요저축액이 앞으로 유지할 영구 기준이 된다.
        MonthlySavingPlanVO latestPlan = monthlySavingPlanMapper.selectLatestBySegment(activeSegment.getSegmentId())
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "PRODUCTS_NOT_SELECTED", "이 구간의 저축 제안이 아직 없습니다."));
        BigDecimal compositionBasis = SPREAD.equals(latestPlan.getDeficitChoice())
                ? latestPlan.getRequiredSnapshot() : baselineAmount;

        // 가장 최근에 커밋된 회차(latestPlan)가 아직 실제 거래(transaction_history)로 안 잡혔을 수
        // 있다 — 예: FULL_RECOVERY로 이번 달 확정만 해두고 실제 납입은 아직 안 한 상태. 그 한 달은
        // 구성 기준액이 아니라 그때 확정된 실제 배분액을 써야 한다(안 그러면 부족액을 만회하기로
        // 확정해놓고도 그래프·달성률에 전혀 반영되지 않는다). 이 구간에서 지금까지 몇 회차가
        // "커밋"됐는지(cycle_no 차이로 계산)를 여기서 한 번만 구해 전달한다.
        MonthlySavingPlanVO firstPlanOfActiveSegment = monthlySavingPlanMapper.selectFirstBySegment(activeSegment.getSegmentId())
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "PRODUCTS_NOT_SELECTED", "이 구간의 저축 제안이 아직 없습니다."));
        int committedCyclesInSegment = latestPlan.getCycleNo() - firstPlanOfActiveSegment.getCycleNo() + 1;
        Map<Long, BigDecimal> committedAllocationBySubscriptionId = monthlySavingAllocationMapper
                .selectByPlanId(latestPlan.getMonthlySavingPlanId()).stream()
                .collect(Collectors.toMap(
                        MonthlySavingAllocationVO::getProductSubscriptionId, MonthlySavingAllocationVO::getAllocatedAmount));

        List<RoadmapGraph.SegmentSummary> summaries = new ArrayList<>();
        int monthsSoFar = 0;
        for (RoadmapSegmentVO segment : realSegments) {
            RoadmapGraph.SegmentSummary summary = COMPLETED.equals(segment.getStatus())
                    ? summarizeCompletedSegment(segment)
                    : summarizeActiveSegment(segment, compositionBasis, committedCyclesInSegment,
                            latestPlan.getRecommendedCashSaving(), committedAllocationBySubscriptionId);
            summaries.add(summary);
            monthsSoFar += segment.getPlannedMonths();
        }

        // STEP 2. 미래 구간 — 현재 구간이 끝난 시점부터 로드맵 끝까지, §3-2 규칙으로 재귀
        // 시뮬레이션. 목돈은 직전 구간의 예상 만기금(적금+예금+이자)+현금성을 그대로 이어받는다.
        // 우대조건은 새로 묻지 않고 이미 저장된 응답을 그대로 재사용한다.
        Map<String, Boolean> willMeetByCondition = memberRateConditionResponseMapper.selectByMemberId(memberId)
                .stream()
                .collect(Collectors.toMap(MemberRateConditionResponseVO::getConditionCode, MemberRateConditionResponseVO::getWillMeet));

        LocalDate cursor = activeSegment.getEndDate();
        int nextSegmentNo = activeSegment.getSegmentNo() + 1;
        RoadmapGraph.SegmentSummary previous = summaries.get(summaries.size() - 1);
        while (monthsSoFar < roadmap.getTotalMonths()) {
            BigDecimal lumpSum = previous.savingsAmount().add(previous.depositAmount())
                    .add(previous.interestAmount()).add(previous.cashAmount());

            FirstSegmentPlan futurePlan = roadmapCalculationService.calculateNextSegment(
                    roadmap.getTotalMonths() - monthsSoFar);
            LocalDate segmentEndDate = roadmapCalculationService.calculateSegmentEndDate(
                    cursor, futurePlan.plannedMonths(), futurePlan.isLastSegment(), roadmap.getEndDate());

            RoadmapGraph.SegmentSummary future = simulateFutureSegment(
                    nextSegmentNo, futurePlan.plannedMonths(), cursor, segmentEndDate, lumpSum, compositionBasis,
                    willMeetByCondition);
            summaries.add(future);

            monthsSoFar += futurePlan.plannedMonths();
            cursor = segmentEndDate;
            nextSegmentNo++;
            previous = future;

            // FO-TEN#55 와 같은 유령 구간 문제를 이 시뮬레이션 루프에서는 직접 막는다 —
            // 마지막 구간을 만든 시점에서 바로 멈춘다.
            if (futurePlan.isLastSegment()) {
                break;
            }
        }

        // STEP 3. 최상단 집계. finalAmount 는 "실제로 내 주머니에서 나간 돈"의 합이라
        // savingsAmount 뿐 아니라 cashAmount 도 더한다(상품 한도를 넘쳐 현금으로 남은 것도
        // 저축은 저축이다) — 단 depositAmount 는 새 돈이 아니라 이미 센 돈이 옮겨간 것뿐이라
        // 절대 더하지 않는다. expectedInterestTotal 도 같은 이유로 마지막 구간의 cashAmount
        // 까지 포함해야 두 항의 현금성이 정확히 상쇄돼 순수 이자 합과 일치한다.
        BigDecimal finalAmount = summaries.stream()
                .map(s -> s.savingsAmount().add(s.cashAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        RoadmapGraph.SegmentSummary last = summaries.get(summaries.size() - 1);
        BigDecimal expectedInterestTotal = last.savingsAmount().add(last.depositAmount())
                .add(last.interestAmount()).add(last.cashAmount())
                .subtract(finalAmount);

        return new RoadmapGraph(roadmap.getTotalMonths(), summaries, finalAmount, expectedInterestTotal);
    }

    // 완료 구간 — 전부 실제 값. 적금 원금은 실제 납입 합, 예금 원금은 initial_principal,
    // 이자는 각 구독의 maturity_amount(세전, DB)-원금을 세후로 변환한 값, 현금성은 그 구간
    // 마지막 실제 스냅샷.
    private RoadmapGraph.SegmentSummary summarizeCompletedSegment(RoadmapSegmentVO segment) {
        BigDecimal savingsAmount = BigDecimal.ZERO;
        BigDecimal depositAmount = BigDecimal.ZERO;
        BigDecimal interestAmount = BigDecimal.ZERO;
        for (ProductSubscriptionVO subscription : productSubscriptionMapper.selectBySegment(segment.getSegmentId())) {
            BigDecimal principal;
            if (ROLLOVER_DEPOSIT.equals(subscription.getSubscriptionRole())) {
                principal = subscription.getInitialPrincipal();
                depositAmount = depositAmount.add(principal);
            } else {
                principal = transactionHistoryMapper.selectSavingsPaymentsBySubscription(subscription.getProductSubscriptionId())
                        .stream().map(SavingsPaymentRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                savingsAmount = savingsAmount.add(principal);
            }
            BigDecimal preTaxInterest = subscription.getMaturityAmount().subtract(principal);
            interestAmount = interestAmount.add(roadmapCalculationService.calculateAfterTaxInterest(preTaxInterest));
        }
        BigDecimal cashAmount = assetSnapshotMapper.selectLatestBySegment(segment.getSegmentId())
                .map(AssetSnapshotVO::getCashSavingBalance)
                .orElse(BigDecimal.ZERO);
        return new RoadmapGraph.SegmentSummary(
                segment.getSegmentNo(), segment.getPlannedMonths(), COMPLETED,
                savingsAmount, depositAmount, cashAmount, interestAmount);
    }

    // 현재 구간 — 이미 낸 달은 실적, 이미 확정만 되고 아직 안 낸 한 달은 그 확정 배분액,
    // 그 뒤 남은 달은 "구성 기준액대로 계속 냈다면"을 가정. 새 상품을 고르지 않는다 — 이미
    // 가입된 구독들에 allocate() 로 매달 패턴만 구한다.
    private RoadmapGraph.SegmentSummary summarizeActiveSegment(
            RoadmapSegmentVO segment, BigDecimal compositionBasis, int committedCyclesInSegment,
            BigDecimal committedCashSaving, Map<Long, BigDecimal> committedAllocationBySubscriptionId) {
        List<ProductSubscriptionVO> subscriptions = productSubscriptionMapper.selectBySegment(segment.getSegmentId());
        ProductSubscriptionVO depositSubscription = subscriptions.stream()
                .filter(s -> ROLLOVER_DEPOSIT.equals(s.getSubscriptionRole()))
                .findFirst().orElse(null);
        List<ProductSubscriptionVO> savingsSubscriptions = subscriptions.stream()
                .filter(s -> NEW_SAVINGS.equals(s.getSubscriptionRole()))
                .toList();

        BigDecimal depositAmount = BigDecimal.ZERO;
        BigDecimal interestAmount = BigDecimal.ZERO;
        if (depositSubscription != null) {
            depositAmount = depositSubscription.getInitialPrincipal();
            BigDecimal depositInterest = roadmapCalculationService.calculateDepositInterest(
                    depositAmount, depositSubscription.getExpectedAppliedRate(), depositSubscription.getTermMonths());
            interestAmount = interestAmount.add(roadmapCalculationService.calculateAfterTaxInterest(depositInterest));
        }

        List<ProductAllocationCandidate> candidates = savingsSubscriptions.stream()
                .map(s -> new ProductAllocationCandidate(
                        s.getProductId(), s.getExpectedAppliedRate(), s.getMonthlyPaymentLimitSnapshot()))
                .sorted(Comparator.comparing(ProductAllocationCandidate::appliedRate).reversed())
                .toList();
        AllocationPlan plan = roadmapCalculationService.allocate(candidates, compositionBasis);
        Map<Long, BigDecimal> monthlyByProductId = plan.allocations().stream()
                .collect(Collectors.toMap(AllocationPlan.AllocationEntry::productId, AllocationPlan.AllocationEntry::allocatedAmount));

        BigDecimal savingsAmount = BigDecimal.ZERO;
        int monthsAlreadyPaid = 0;
        for (ProductSubscriptionVO subscription : savingsSubscriptions) {
            List<SavingsPaymentRecord> pastPayments =
                    transactionHistoryMapper.selectSavingsPaymentsBySubscription(subscription.getProductSubscriptionId());
            monthsAlreadyPaid = Math.max(monthsAlreadyPaid, pastPayments.size());
            int monthsRemaining = Math.max(0, subscription.getTermMonths() - pastPayments.size());
            // 이 구독이 실제로 낸 달수(pastPayments.size())보다 이 구간에 커밋된 회차수가 많으면,
            // 그 차이(항상 1개월)는 "확정은 됐지만 아직 거래는 안 잡힌 이번 달"이다 — 구성
            // 기준액이 아니라 그때 확정된 실제 배분액을 그대로 써야 한다.
            boolean hasCommittedUnpaidMonth = committedCyclesInSegment > pastPayments.size();
            BigDecimal committedMonthAmount = committedAllocationBySubscriptionId
                    .getOrDefault(subscription.getProductSubscriptionId(), BigDecimal.ZERO);
            BigDecimal futureMonthlyAmount = monthlyByProductId.getOrDefault(subscription.getProductId(), BigDecimal.ZERO);

            List<SavingsPaymentRecord> allPayments = new ArrayList<>(pastPayments);
            BigDecimal futureSavingsAmount = BigDecimal.ZERO;
            for (int i = 0; i < monthsRemaining; i++) {
                BigDecimal amount = (hasCommittedUnpaidMonth && i == 0) ? committedMonthAmount : futureMonthlyAmount;
                allPayments.add(new SavingsPaymentRecord(amount,
                        subscription.getStartDate().plusMonths((long) pastPayments.size() + i).atStartOfDay()));
                futureSavingsAmount = futureSavingsAmount.add(amount);
            }
            BigDecimal pastPrincipal = pastPayments.stream().map(SavingsPaymentRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            savingsAmount = savingsAmount.add(pastPrincipal).add(futureSavingsAmount);
            BigDecimal savingsInterest = roadmapCalculationService.calculateSavingsInterest(
                    allPayments, subscription.getExpectedAppliedRate(), subscription.getMaturityDate());
            interestAmount = interestAmount.add(roadmapCalculationService.calculateAfterTaxInterest(savingsInterest));
        }

        int remainingMonthsInSegment = Math.max(0, segment.getPlannedMonths() - monthsAlreadyPaid);
        boolean hasCommittedUnpaidMonth = committedCyclesInSegment > monthsAlreadyPaid;
        int compositionBasisMonths = hasCommittedUnpaidMonth ? remainingMonthsInSegment - 1 : remainingMonthsInSegment;
        BigDecimal futureCash = plan.recommendedCashSaving().multiply(BigDecimal.valueOf(Math.max(0, compositionBasisMonths)));
        if (hasCommittedUnpaidMonth && remainingMonthsInSegment > 0) {
            futureCash = futureCash.add(committedCashSaving);
        }
        BigDecimal lastKnownCash = assetSnapshotMapper.selectLatestBySegment(segment.getSegmentId())
                .map(AssetSnapshotVO::getCashSavingBalance)
                .orElse(BigDecimal.ZERO);
        BigDecimal cashAmount = lastKnownCash.add(futureCash);

        return new RoadmapGraph.SegmentSummary(
                segment.getSegmentNo(), segment.getPlannedMonths(), ACTIVE,
                savingsAmount, depositAmount, cashAmount, interestAmount);
    }

    // 미래 구간 — 아직 구독이 없어서 예금·적금 둘 다 그 구간 길이에 맞게 새로 후보를 뽑는다
    // (현재 확정 금리·본인이 이미 저장해둔 우대조건 응답 기준, 미래 금리는 예측하지 않는다).
    private RoadmapGraph.SegmentSummary simulateFutureSegment(
            int segmentNo, int plannedMonths, LocalDate startDate, LocalDate endDate,
            BigDecimal lumpSum, BigDecimal compositionBasis, Map<String, Boolean> willMeetByCondition) {

        BigDecimal depositAmount = BigDecimal.ZERO;
        BigDecimal interestAmount = BigDecimal.ZERO;
        BigDecimal undepositedLumpSum = lumpSum;

        List<DepositRateCandidate> depositCandidates = productMapper.selectDepositCandidates(plannedMonths);
        Map<Long, BigDecimal> depositBonus = computeBonusByProductId(
                depositCandidates.stream().map(DepositRateCandidate::productId).toList(), willMeetByCondition);
        List<RatedDepositCandidate> rankedDeposits = depositCandidates.stream()
                .map(c -> new RatedDepositCandidate(
                        c.productId(),
                        roadmapCalculationService.calculateExpectedAppliedRate(
                                c.maxRate(), c.baseRate(), depositBonus.getOrDefault(c.productId(), BigDecimal.ZERO)),
                        c.minSubscriptionAmount()))
                .sorted(Comparator.comparing(RatedDepositCandidate::appliedRate).reversed())
                .toList();
        Optional<RatedDepositCandidate> chosenDeposit = roadmapCalculationService.selectDeposit(rankedDeposits, lumpSum);
        if (chosenDeposit.isPresent()) {
            depositAmount = lumpSum;
            undepositedLumpSum = BigDecimal.ZERO;
            BigDecimal depositInterest = roadmapCalculationService.calculateDepositInterest(
                    lumpSum, chosenDeposit.get().appliedRate(), plannedMonths);
            interestAmount = interestAmount.add(roadmapCalculationService.calculateAfterTaxInterest(depositInterest));
        }

        List<ProductRateCandidate> savingsCandidates = productMapper.selectSavingsCandidates(plannedMonths);
        Map<Long, BigDecimal> savingsBonus = computeBonusByProductId(
                savingsCandidates.stream().map(ProductRateCandidate::productId).toList(), willMeetByCondition);
        List<ProductAllocationCandidate> rankedSavings = savingsCandidates.stream()
                .map(c -> new ProductAllocationCandidate(
                        c.productId(),
                        roadmapCalculationService.calculateExpectedAppliedRate(
                                c.maxRate(), c.baseRate(), savingsBonus.getOrDefault(c.productId(), BigDecimal.ZERO)),
                        c.monthlyPaymentLimit()))
                .sorted(Comparator.comparing(ProductAllocationCandidate::appliedRate).reversed())
                .toList();
        Map<Long, BigDecimal> savingsRateByProductId = rankedSavings.stream()
                .collect(Collectors.toMap(ProductAllocationCandidate::productId, ProductAllocationCandidate::appliedRate));
        AllocationPlan plan = roadmapCalculationService.allocate(rankedSavings, compositionBasis);

        BigDecimal savingsAmount = BigDecimal.ZERO;
        for (AllocationPlan.AllocationEntry entry : plan.allocations()) {
            List<SavingsPaymentRecord> payments = new ArrayList<>();
            for (int i = 0; i < plannedMonths; i++) {
                payments.add(new SavingsPaymentRecord(entry.allocatedAmount(), startDate.plusMonths(i).atStartOfDay()));
            }
            savingsAmount = savingsAmount.add(entry.allocatedAmount().multiply(BigDecimal.valueOf(plannedMonths)));
            BigDecimal savingsInterest = roadmapCalculationService.calculateSavingsInterest(
                    payments, savingsRateByProductId.get(entry.productId()), endDate);
            interestAmount = interestAmount.add(roadmapCalculationService.calculateAfterTaxInterest(savingsInterest));
        }

        // 목돈이 예금 최소가입금액에 못 미쳐 예금이 안 열리면(드문 경우지만) 목돈 전체가
        // 현금성으로 이월된다 — 매달 남는 현금성(overflow × 개월수)에 이걸 더하는 안전장치.
        BigDecimal cashAmount = plan.recommendedCashSaving().multiply(BigDecimal.valueOf(plannedMonths))
                .add(undepositedLumpSum);

        return new RoadmapGraph.SegmentSummary(segmentNo, plannedMonths, FUTURE, savingsAmount, depositAmount, cashAmount, interestAmount);
    }

    // 우대조건 응답 중 will_meet=true 인 것만 반영해 상품별 우대금리 합을 구한다 (§4-3, §4-4).
    private Map<Long, BigDecimal> computeBonusByProductId(List<Long> productIds, Map<String, Boolean> willMeetByCondition) {
        Map<Long, BigDecimal> bonusByProductId = new HashMap<>();
        if (productIds.isEmpty()) {
            return bonusByProductId;
        }
        for (ProductPreferentialRateVO rate : productPreferentialRateMapper.selectByProductIds(productIds)) {
            if (Boolean.TRUE.equals(willMeetByCondition.get(rate.getConditionCode()))) {
                bonusByProductId.merge(rate.getProductId(), rate.getRateBonus(), BigDecimal::add);
            }
        }
        return bonusByProductId;
    }

    @Override
    public SegmentDetail getSegmentDetail(long memberId) {
        // getStatus() 를 그대로 재사용 — lastMonthActualAmount/currentSegmentNo/baselineAmount
        // 모두 거기서만 계산되는 값이라 따로 다시 구하지 않는다.
        RoadmapStatus status = getStatus(memberId);

        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        LocalDate thisMonth = YearMonth.now().atDay(1);
        MonthlySavingPlanVO currentPlan = monthlySavingPlanMapper
                .selectByRoadmapAndMonth(roadmap.getSavingsRoadmapId(), thisMonth)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "MONTHLY_SAVING_NOT_CONFIRMED", "이번 달 저축 방식이 아직 확정되지 않았습니다(4-6 먼저 호출 필요)."));

        // "다음 달부터" = 구성 기준액(§4-7과 동일 규칙) — SPREAD면 그때 얼려둔 필요저축액이
        // 영구 기준, 아니면(NONE/FULL_RECOVERY) 목표기준액.
        BigDecimal futureAmount = SPREAD.equals(currentPlan.getDeficitChoice())
                ? currentPlan.getRequiredSnapshot() : status.baselineAmount();

        // 지난달 막대는 지난달이 이 구간에 속할 때만 그린다. 구간이 막 바뀐 첫 달의 지난달은 지난
        // 구간 마지막 달이라, 새 구간 계획과 나란히 놓으면 다른 기준의 금액을 견주게 된다.
        RoadmapSegmentVO activeSegment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));
        boolean lastMonthInSegment = monthlySavingPlanMapper
                .selectByRoadmapAndMonth(roadmap.getSavingsRoadmapId(), thisMonth.minusMonths(1))
                .map(p -> Objects.equals(p.getSegmentId(), activeSegment.getSegmentId()))
                .orElse(false);

        List<SegmentDetail.Bar> bars = new ArrayList<>();
        if (status.lastMonthActualAmount() != null && lastMonthInSegment) {
            bars.add(new SegmentDetail.Bar("지난달", "ACTUAL", status.lastMonthActualAmount()));
        }
        bars.add(new SegmentDetail.Bar("이번 달", "PLAN", currentPlan.getMonthlySavingAmount()));
        bars.add(new SegmentDetail.Bar("다음 달부터", "FUTURE", futureAmount));

        return new SegmentDetail(status.currentSegmentNo(), currentPlan.getDeficitChoice(), status.baselineAmount(), bars);
    }

    @Override
    public MonthlyPlanSummary getCurrentMonthlyPlan(long memberId) {
        // 새 계산 없음 — 4-4/4-6에서 이미 확정해둔 값을 그대로 보여주기만 한다 (§4-9).
        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        LocalDate thisMonth = YearMonth.now().atDay(1);
        MonthlySavingPlanVO plan = monthlySavingPlanMapper
                .selectByRoadmapAndMonth(roadmap.getSavingsRoadmapId(), thisMonth)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "MONTHLY_SAVING_NOT_CONFIRMED", "이번 달 저축 방식이 아직 확정되지 않았습니다(4-6 먼저 호출 필요)."));
        RoadmapSegmentVO segment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));

        Map<Long, ProductSubscriptionVO> subscriptionById = productSubscriptionMapper.selectBySegment(segment.getSegmentId())
                .stream()
                .collect(Collectors.toMap(ProductSubscriptionVO::getProductSubscriptionId, s -> s));
        List<MonthlySavingAllocationVO> allocations = monthlySavingAllocationMapper.selectByPlanId(plan.getMonthlySavingPlanId());
        Map<Long, ProductVO> productById = allocations.isEmpty()
                ? Map.of()
                : productMapper.selectByIds(allocations.stream()
                        .map(a -> subscriptionById.get(a.getProductSubscriptionId()).getProductId())
                        .toList())
                        .stream()
                        .collect(Collectors.toMap(ProductVO::getProductId, p -> p));

        List<MonthlyPlanSummary.AllocationSummary> allocationSummaries = allocations.stream()
                .map(a -> {
                    ProductSubscriptionVO subscription = subscriptionById.get(a.getProductSubscriptionId());
                    return new MonthlyPlanSummary.AllocationSummary(
                            productById.get(subscription.getProductId()).getProductName(),
                            subscription.getExpectedAppliedRate(),
                            a.getAllocatedAmount(),
                            subscription.getMonthlyPaymentLimitSnapshot());
                })
                .toList();

        return new MonthlyPlanSummary(
                plan.getPlanMonth(), plan.getMonthlySavingAmount(), allocationSummaries,
                plan.getRecommendedCashSaving(), plan.getMonthlySavingAmount());
    }

    @Override
    public RoadmapProjection getProjection(long memberId) {
        // getGraph() 를 그대로 재사용 — "원금 합계(finalAmount) + 예상 이자" 가 곧 마지막
        // 구간의 총자산과 같다는 걸 이미 그쪽에서 검증해뒀다.
        RoadmapGraph graph = getGraph(memberId);

        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        MonthlySavingPlanVO firstPlan = monthlySavingPlanMapper.selectFirst(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "PRODUCTS_NOT_SELECTED", "최초 저축 계획이 아직 없습니다."));
        // 목표저축액(KRW) — §2-3 역산 공식, getStatus() STEP6과 동일한 계산.
        BigDecimal targetAmount = roadmapCalculationService.reverseTargetAmount(
                firstPlan.getBaselineSnapshot(), roadmap.getTotalMonths(), firstPlan.getCurrentAccumulatedFund());

        // 목표 달성률(%) = (원금 합계 + 예상 이자) ÷ 목표저축액(KRW) × 100
        BigDecimal totalAsset = graph.finalAmount().add(graph.expectedInterestTotal());
        BigDecimal achievementRate = totalAsset.multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 0, RoundingMode.HALF_UP);

        return new RoadmapProjection(graph.expectedInterestTotal(), achievementRate);
    }
}
