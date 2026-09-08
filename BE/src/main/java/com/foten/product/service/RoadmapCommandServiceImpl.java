package com.foten.product.service;

import com.foten.common.InvalidRequestException;
import com.foten.common.ResourceNotFoundException;
import com.foten.common.RoadmapStateConflictException;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.StayInfo;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.StayInfoMapper;
import com.foten.product.domain.AllocationPlan;
import com.foten.product.domain.AllocationPlan.AllocationEntry;
import com.foten.product.domain.AssetSnapshotVO;
import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.DeficitChoiceResult;
import com.foten.product.domain.DepositRateCandidate;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.MemberRateConditionResponseVO;
import com.foten.product.domain.MonthlySavingAllocationVO;
import com.foten.product.domain.MonthlySavingPlanVO;
import com.foten.product.domain.ProductAllocationCandidate;
import com.foten.product.domain.ProductPreferentialRateVO;
import com.foten.product.domain.ProductRateCandidate;
import com.foten.product.domain.ProductSubscriptionVO;
import com.foten.product.domain.ProductVO;
import com.foten.product.domain.RateConditionAnswer;
import com.foten.product.domain.RatedDepositCandidate;
import com.foten.product.domain.RoadmapProjection;
import com.foten.product.domain.RoadmapSegmentVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SavingsPaymentRecord;
import com.foten.product.domain.SavingsRoadmapVO;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.mapper.AssetSnapshotMapper;
import com.foten.product.mapper.MemberRateConditionResponseMapper;
import com.foten.product.mapper.MonthlySavingAllocationMapper;
import com.foten.product.mapper.MonthlySavingPlanMapper;
import com.foten.product.mapper.ProductMapper;
import com.foten.product.mapper.ProductPreferentialRateMapper;
import com.foten.product.mapper.ProductSubscriptionMapper;
import com.foten.product.mapper.RoadmapSegmentMapper;
import com.foten.product.mapper.SavingsRoadmapMapper;
import com.foten.product.mapper.TransactionHistoryMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoadmapCommandServiceImpl implements RoadmapCommandService {

    private static final int FIRST_SEGMENT_NO = 1;
    private static final String ONBOARDING = "ONBOARDING";
    private static final String NEW_SEGMENT = "NEW_SEGMENT";
    private static final String NEW_SAVINGS = "NEW_SAVINGS";
    private static final String ROLLOVER_DEPOSIT = "ROLLOVER_DEPOSIT";
    private static final String NO_DEFICIT = "NONE";
    private static final String FULL_RECOVERY = "FULL_RECOVERY";
    private static final String SPREAD = "SPREAD";

    private final SavingsRoadmapMapper savingsRoadmapMapper;
    private final RoadmapSegmentMapper roadmapSegmentMapper;
    private final MemberRateConditionResponseMapper memberRateConditionResponseMapper;
    private final ProductMapper productMapper;
    private final ProductPreferentialRateMapper productPreferentialRateMapper;
    private final ProductSubscriptionMapper productSubscriptionMapper;
    private final MonthlySavingPlanMapper monthlySavingPlanMapper;
    private final MonthlySavingAllocationMapper monthlySavingAllocationMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;
    private final TransactionHistoryMapper transactionHistoryMapper;
    private final GoalMapper goalMapper; // 교차 도메인, 읽기 전용
    private final StayInfoMapper stayInfoMapper; // 교차 도메인, 읽기 전용
    private final RoadmapQueryService roadmapQueryService;
    private final RoadmapCalculationService roadmapCalculationService;

    @Override
    @Transactional
    public CreatedRoadmap createRoadmap(long memberId) {
        // STEP 1. 이미 로드맵이 있으면 재생성하지 않는다 (savings_roadmap 은 회원당 1건).
        if (savingsRoadmapMapper.selectByMemberId(memberId).isPresent()) {
            throw new RoadmapStateConflictException(
                    "ROADMAP_ALREADY_EXISTS", "이미 로드맵이 있습니다.");
        }

        // STEP 2. 목표기준액·체류정보는 이 도메인이 계산하지 않고 이미 확정돼 있어야 하는
        // 전제조건이다 (설계 원칙 7). 없으면 아직 온보딩이 끝나지 않은 것.
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "목표가 아직 확정되지 않았습니다."));
        LocalDate expectedReturnDate = stayInfoMapper.selectByMemberId(memberId)
                .map(StayInfo::getExpectedReturnDate)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "체류 정보가 아직 확정되지 않았습니다."));

        // STEP 3. 로드맵 기간 계산 (로직 v3 §3-1) — 오늘부터 "예상 귀국일 - 1개월"까지.
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = expectedReturnDate.minusMonths(1);
        int totalMonths = roadmapCalculationService.calculateRemainingMonths(startDate, endDate);

        SavingsRoadmapVO roadmap = SavingsRoadmapVO.builder()
                .memberId(memberId)
                .startDate(startDate)
                .endDate(endDate)
                .totalMonths(totalMonths)
                .build();
        savingsRoadmapMapper.insert(roadmap); // insert 후 roadmap.savingsRoadmapId 가 채워진다

        // STEP 4. 첫 구간(segment_no=1) 생성 (§3-2). 상품 가입은 아직 하지 않는다 — 우대조건 응답 대기.
        FirstSegmentPlan firstSegment = roadmapCalculationService.calculateFirstSegment(totalMonths);
        LocalDate segmentEndDate = roadmapCalculationService.calculateSegmentEndDate(
                startDate, firstSegment.plannedMonths(), firstSegment.isLastSegment(), endDate);

        RoadmapSegmentVO segment = RoadmapSegmentVO.builder()
                .savingsRoadmapId(roadmap.getSavingsRoadmapId())
                .segmentNo(FIRST_SEGMENT_NO)
                .plannedMonths(firstSegment.plannedMonths())
                .startDate(startDate)
                .endDate(segmentEndDate)
                .isLastSegment(firstSegment.isLastSegment())
                .build();
        roadmapSegmentMapper.insert(segment);

        // STEP 5. 필요저축액은 이 시점엔 목표기준액과 같다 — 아직 과거 실적이 없으므로 (§2-3).
        return new CreatedRoadmap(
                totalMonths,
                baselineAmount,
                baselineAmount,
                new CreatedRoadmap.SegmentSummary(
                        FIRST_SEGMENT_NO,
                        firstSegment.plannedMonths(),
                        startDate,
                        segmentEndDate,
                        firstSegment.isLastSegment()));
    }

    @Override
    @Transactional
    public SegmentComposition submitRateConditionResponses(
            long memberId, List<RateConditionAnswer> responses, String deficitChoice) {
        // STEP 1. ONBOARDING 또는 NEW_SEGMENT 만 허용한다. REGULAR_MONTH 는 우대조건을 다시
        // 확인할 시점이 아니라 막는다.
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        boolean isNewSegment = NEW_SEGMENT.equals(status.flowType());
        if (!ONBOARDING.equals(status.flowType()) && !isNewSegment) {
            throw new RoadmapStateConflictException(
                    "NOT_APPLICABLE", "우대조건을 다시 확인할 시점이 아닙니다. flowType=" + status.flowType());
        }
        if (isNewSegment && Boolean.TRUE.equals(status.hasShortfall()) && deficitChoice == null) {
            throw new InvalidRequestException(
                    "DEFICIT_CHOICE_REQUIRED", "부족액이 있는 구간 전환에는 deficitChoice가 필요합니다.");
        }

        // STEP 2. 우대조건 응답 UPSERT (§4-3). 최신 응답만 유지한다.
        List<MemberRateConditionResponseVO> responseVOs = responses.stream()
                .map(r -> MemberRateConditionResponseVO.builder()
                        .memberId(memberId)
                        .conditionCode(r.conditionCode())
                        .willMeet(r.willMeet())
                        .build())
                .toList();
        memberRateConditionResponseMapper.upsertAll(responseVOs);
        Map<String, Boolean> willMeetByCondition = responses.stream()
                .collect(Collectors.toMap(RateConditionAnswer::conditionCode, RateConditionAnswer::willMeet));

        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        RoadmapSegmentVO activeSegment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "목표가 아직 확정되지 않았습니다."));

        RoadmapSegmentVO targetSegment = activeSegment; // ONBOARDING 이면 상품을 가입시킬 구간이 곧 activeSegment
        BigDecimal rolloverAmount = null;
        SegmentComposition.DepositSummary depositSummary = null;
        BigDecimal newSegmentDepositPrincipal = BigDecimal.ZERO; // 새 구간에 예금을 열었으면 그 원금

        if (isNewSegment) {
            // STEP 3-A. 직전 구간(activeSegment) 마감 — 구독별 만기 이자 계산 → MATURED,
            // 구간 자체도 COMPLETED. 적금이 여러 개일 수 있어 전부 순회한다.
            BigDecimal lumpSum = BigDecimal.ZERO;
            for (ProductSubscriptionVO subscription : productSubscriptionMapper.selectActiveBySegment(activeSegment.getSegmentId())) {
                MaturityBreakdown breakdown = calculateMaturityBreakdown(subscription);
                BigDecimal maturityAmount = breakdown.principal().add(breakdown.preTaxInterest()); // 세전 — DB 저장용
                productSubscriptionMapper.matureAndClose(subscription.getProductSubscriptionId(), maturityAmount);
                BigDecimal afterTaxInterest = roadmapCalculationService.calculateAfterTaxInterest(breakdown.preTaxInterest());
                lumpSum = lumpSum.add(breakdown.principal()).add(afterTaxInterest); // 세후 — 실제 롤오버 금액(§7-2)
            }
            // 목돈 합산(§7-2) = 위 만기금 합 + 그 구간 마지막 실제 현금성 저축액.
            BigDecimal cashSavingBalance = assetSnapshotMapper.selectLatest(roadmap.getSavingsRoadmapId())
                    .map(AssetSnapshotVO::getCashSavingBalance)
                    .orElse(BigDecimal.ZERO);
            lumpSum = lumpSum.add(cashSavingBalance);
            roadmapSegmentMapper.complete(activeSegment.getSegmentId());
            rolloverAmount = lumpSum;

            // STEP 3-B. 새 구간 생성 (§3-2 규칙을 "남은 개월수"에 재적용). 직전 구간이 끝난
            // 시점부터 이어서 시작한다(대화한 날짜가 아니라 — 목돈이 노는 공백을 두지 않음).
            int remainingMonths = roadmapCalculationService.calculateRemainingMonths(
                    activeSegment.getEndDate(), roadmap.getEndDate());
            FirstSegmentPlan nextSegmentPlan = roadmapCalculationService.calculateFirstSegment(remainingMonths);
            LocalDate newSegmentEndDate = roadmapCalculationService.calculateSegmentEndDate(
                    activeSegment.getEndDate(), nextSegmentPlan.plannedMonths(),
                    nextSegmentPlan.isLastSegment(), roadmap.getEndDate());
            RoadmapSegmentVO newSegment = RoadmapSegmentVO.builder()
                    .savingsRoadmapId(roadmap.getSavingsRoadmapId())
                    .segmentNo(activeSegment.getSegmentNo() + 1)
                    .plannedMonths(nextSegmentPlan.plannedMonths())
                    .startDate(activeSegment.getEndDate())
                    .endDate(newSegmentEndDate)
                    .isLastSegment(nextSegmentPlan.isLastSegment())
                    .build();
            roadmapSegmentMapper.insert(newSegment);
            targetSegment = newSegment;

            // STEP 3-C. 예금 판단 (§6-2~6-4) — 목돈이 최소가입금액 이상인 최고금리 후보 하나만.
            List<DepositRateCandidate> depositCandidates =
                    productMapper.selectDepositCandidates(newSegment.getPlannedMonths());
            Map<Long, BigDecimal> depositBonusByProductId = computeBonusByProductId(
                    depositCandidates.stream().map(DepositRateCandidate::productId).toList(), willMeetByCondition);
            List<RatedDepositCandidate> rankedDeposits = depositCandidates.stream()
                    .map(c -> new RatedDepositCandidate(
                            c.productId(),
                            roadmapCalculationService.calculateExpectedAppliedRate(
                                    c.maxRate(), c.baseRate(), depositBonusByProductId.getOrDefault(c.productId(), BigDecimal.ZERO)),
                            c.minSubscriptionAmount()))
                    .sorted(Comparator.comparing(RatedDepositCandidate::appliedRate).reversed())
                    .toList();

            Optional<RatedDepositCandidate> chosenDeposit = roadmapCalculationService.selectDeposit(rankedDeposits, lumpSum);
            if (chosenDeposit.isPresent()) {
                RatedDepositCandidate chosen = chosenDeposit.get();
                ProductSubscriptionVO depositSubscription = ProductSubscriptionVO.builder()
                        .memberId(memberId)
                        .productId(chosen.productId())
                        .segmentId(newSegment.getSegmentId())
                        .subscriptionRole(ROLLOVER_DEPOSIT)
                        .termMonths(newSegment.getPlannedMonths())
                        .startDate(newSegment.getStartDate())
                        .maturityDate(newSegment.getEndDate())
                        .expectedAppliedRate(chosen.appliedRate())
                        .initialPrincipal(lumpSum)
                        .build();
                productSubscriptionMapper.insert(depositSubscription);
                newSegmentDepositPrincipal = lumpSum;

                BigDecimal depositInterest = roadmapCalculationService.calculateDepositInterest(
                        lumpSum, chosen.appliedRate(), newSegment.getPlannedMonths());
                BigDecimal afterTaxInterest = roadmapCalculationService.calculateAfterTaxInterest(depositInterest);
                String depositProductName = productMapper.selectByIds(List.of(chosen.productId())).stream()
                        .findFirst().map(ProductVO::getProductName).orElse(null);
                depositSummary = new SegmentComposition.DepositSummary(
                        depositProductName, newSegment.getPlannedMonths(), chosen.appliedRate(),
                        lumpSum, lumpSum.add(afterTaxInterest), afterTaxInterest);
            }
            // chosenDeposit 이 없으면(최소가입금액 미달 또는 후보 없음) 예금 미가입 — lumpSum 은
            // rolloverAmount 로만 안내되고 현금성으로 이월된다(자산 스냅샷 반영은 별도 배치 영역).
        }

        // STEP 4 (공통). 이 구간 기간에 맞는 자유적립식 적금 후보 조회 + 예상 적용금리 계산 후 정렬
        // (§4-2, §4-4, §4-5). "마지막 구간이라 상품 1개만" 같은 특별 취급은 없다 — 후보 자체가
        // 이 기간을 커버하는 상품만 걸러진 결과라 allocate() 가 그대로 여러 상품에 나눠 채운다.
        List<ProductRateCandidate> savingsCandidates = productMapper.selectSavingsCandidates(targetSegment.getPlannedMonths());
        Map<Long, BigDecimal> savingsBonusByProductId = computeBonusByProductId(
                savingsCandidates.stream().map(ProductRateCandidate::productId).toList(), willMeetByCondition);
        List<RatedCandidate> rankedSavings = savingsCandidates.stream()
                .map(c -> new RatedCandidate(c, roadmapCalculationService.calculateExpectedAppliedRate(
                        c.maxRate(), c.baseRate(), savingsBonusByProductId.getOrDefault(c.productId(), BigDecimal.ZERO))))
                .sorted(Comparator.comparing(RatedCandidate::appliedRate).reversed())
                .toList();
        List<ProductAllocationCandidate> allocationCandidates = rankedSavings.stream()
                .map(rc -> new ProductAllocationCandidate(
                        rc.product().productId(), rc.appliedRate(), rc.product().monthlyPaymentLimit()))
                .toList();

        // STEP 5 (공통). 구성 기준액/당월저축액 결정 (§4-7, §5-2) — 4-6과 공유하는 계산이라
        // private 헬퍼로 뺐다. ONBOARDING 은 부족액이 있을 수 없어 둘 다 목표기준액과 같다.
        BigDecimal compositionBasis = computeProductBaselineAmount(status, baselineAmount, deficitChoice);
        BigDecimal monthlySavingAmount = computeMonthlySavingAmount(status, baselineAmount, deficitChoice);

        // STEP 6. 1차 배분 — "구성 기준액"으로 이번 구간에 실제 가입할 상품 집합을 확정한다.
        // (4-5 GET .../composition 이 나중에 이 기준액으로 다시 계산해서 "앞으로 유지할 기준"을
        // 보여줄 것이므로, 여기서 만드는 구독 자체도 이 기준에 맞아야 한다.)
        AllocationPlan basisPlan = roadmapCalculationService.allocate(allocationCandidates, compositionBasis);
        Set<Long> selectedProductIds = basisPlan.allocations().stream()
                .map(AllocationEntry::productId).collect(Collectors.toCollection(HashSet::new));
        List<ProductAllocationCandidate> selectedCandidates = allocationCandidates.stream()
                .filter(c -> selectedProductIds.contains(c.productId()))
                .toList(); // allocationCandidates 가 이미 정렬돼 있어 필터링해도 순서가 유지된다

        // STEP 7. 선택된 상품마다 구독(NEW_SAVINGS) 생성.
        Map<Long, RatedCandidate> rankedById = rankedSavings.stream()
                .collect(Collectors.toMap(rc -> rc.product().productId(), rc -> rc));
        Map<Long, Long> subscriptionIdByProductId = new HashMap<>();
        for (ProductAllocationCandidate candidate : selectedCandidates) {
            RatedCandidate ranked = rankedById.get(candidate.productId());
            ProductSubscriptionVO subscription = ProductSubscriptionVO.builder()
                    .memberId(memberId)
                    .productId(candidate.productId())
                    .segmentId(targetSegment.getSegmentId())
                    .subscriptionRole(NEW_SAVINGS)
                    .termMonths(targetSegment.getPlannedMonths())
                    .startDate(targetSegment.getStartDate())
                    .maturityDate(targetSegment.getEndDate())
                    .expectedAppliedRate(ranked.appliedRate())
                    .monthlyPaymentLimitSnapshot(ranked.product().monthlyPaymentLimit())
                    .build();
            productSubscriptionMapper.insert(subscription); // insert 후 productSubscriptionId 채워짐
            subscriptionIdByProductId.put(candidate.productId(), subscription.getProductSubscriptionId());
        }

        // STEP 8. 2차 배분 — 선택된 상품들(만)로, 이번 달 실제 저축액(당월저축액)을 다시 채운다.
        // 구성 기준액보다 당월저축액이 크면(만회분 포함) 같은 상품 한도 안에서 더 채워지고,
        // 그래도 남으면 그때 추천 현금성 저축액으로 빠진다.
        AllocationPlan actualPlan = roadmapCalculationService.allocate(selectedCandidates, monthlySavingAmount);
        List<SegmentComposition.SavingsSummary> savingsSummaries = new ArrayList<>();
        for (AllocationEntry entry : actualPlan.allocations()) {
            RatedCandidate ranked = rankedById.get(entry.productId());
            savingsSummaries.add(new SegmentComposition.SavingsSummary(
                    ranked.product().productName(), targetSegment.getPlannedMonths(), ranked.appliedRate(),
                    ranked.product().monthlyPaymentLimit(), entry.allocatedAmount()));
        }

        // STEP 9. 당월 저축 계획 생성. NEW_SEGMENT 는 로드맵 시작일부터의 실제 회차(status.cycleNo)를
        // 그대로 잇는다 — 구간이 바뀌어도 cycle_no 는 로드맵 전체 기준이라 새로 1부터 세지 않는다.
        BigDecimal currentAccumulatedFund = isNewSegment
                ? roadmapCalculationService.calculateCurrentAccumulatedFund(
                        newSegmentDepositPrincipal, BigDecimal.ZERO, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal cumulativeSavingPerformance = isNewSegment
                ? calculateCumulativeSavingPerformanceNow(memberId, roadmap)
                : BigDecimal.ZERO;
        int cycleNo = isNewSegment ? status.cycleNo() : 1;

        // planMonth: NEW_SEGMENT는 cycleNo 자체가 "오늘" 기준(calculateCycleNo)이라 오늘 달을 쓰고,
        // ONBOARDING은 cycleNo=1이 "구간 시작월" 기준이라 로드맵 생성일과 응답 제출일이 다른 달에
        // 걸쳐도 어긋나지 않게 구간 시작일 기준을 쓴다.
        LocalDate planMonthBasis = isNewSegment ? LocalDate.now() : targetSegment.getStartDate();
        MonthlySavingPlanVO monthlySavingPlan = MonthlySavingPlanVO.builder()
                .savingsRoadmapId(roadmap.getSavingsRoadmapId())
                .segmentId(targetSegment.getSegmentId())
                .planMonth(YearMonth.from(planMonthBasis).atDay(1))
                .cycleNo(cycleNo)
                .deficitChoice(deficitChoice != null ? deficitChoice : NO_DEFICIT)
                .monthlySavingAmount(monthlySavingAmount)
                .recommendedCashSaving(actualPlan.recommendedCashSaving())
                .currentAccumulatedFund(currentAccumulatedFund)
                .cumulativeSavingPerformance(cumulativeSavingPerformance)
                .baselineSnapshot(baselineAmount)
                .requiredSnapshot(monthlySavingAmount)
                .build();
        monthlySavingPlanMapper.insert(monthlySavingPlan); // insert 후 monthlySavingPlanId 채워짐

        for (AllocationEntry entry : actualPlan.allocations()) {
            MonthlySavingAllocationVO allocation = MonthlySavingAllocationVO.builder()
                    .monthlySavingPlanId(monthlySavingPlan.getMonthlySavingPlanId())
                    .productSubscriptionId(subscriptionIdByProductId.get(entry.productId()))
                    .allocatedAmount(entry.allocatedAmount())
                    .allocationOrder(entry.allocationOrder())
                    .build();
            monthlySavingAllocationMapper.insert(allocation);
        }

        // STEP 10. 필요저축액 컬럼 갱신 — 이 트랜잭션이 이번 달 필요저축액을 확정하는 유일한 지점
        // (설계 원칙 7). target_baseline_amount 는 여기서도 건드리지 않는다.
        goalMapper.updateMonthlyRequiredSaving(memberId, monthlySavingAmount);

        // STEP 11. 전체 로드맵 투영(§10) — 방금 커밋한 이 행이 반영된 값이어야 해서 insert
        // 이후에 계산한다(getGraph() 가 "가장 최근 회차"를 다시 조회하기 때문). 같은 값을
        // 응답과 이 행(projected_total_interest) 양쪽에 채운다.
        RoadmapProjection projection = roadmapQueryService.getProjection(memberId);
        monthlySavingPlanMapper.updateProjectedTotalInterest(
                monthlySavingPlan.getMonthlySavingPlanId(), projection.expectedInterestTotal());

        return new SegmentComposition(
                baselineAmount, rolloverAmount, depositSummary, savingsSummaries,
                actualPlan.recommendedCashSaving(), projection.expectedInterestTotal(), projection.achievementRate());
    }

    @Override
    @Transactional
    public DeficitChoiceResult confirmDeficitChoice(long memberId, String choice) {
        // STEP 1. ONBOARDING(§3-1 흐름표에 이 호출이 없음) 또는 로드맵 자체가 없으면 대상이 아니다.
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        if (!status.roadmapExists() || ONBOARDING.equals(status.flowType())) {
            throw new RoadmapStateConflictException(
                    "NOT_APPLICABLE", "지금은 당월 저축 방식을 확정할 시점이 아닙니다. flowType=" + status.flowType());
        }
        if (Boolean.TRUE.equals(status.hasShortfall()) && choice == null) {
            throw new InvalidRequestException(
                    "DEFICIT_CHOICE_REQUIRED", "부족액이 있는 달에는 choice가 필요합니다.");
        }
        // 부족액이 없는데 choice가 왔으면 에러가 아니라 그냥 무시하고 NONE 취급한다 (§4-6).
        String effectiveChoice = Boolean.TRUE.equals(status.hasShortfall()) ? choice : NO_DEFICIT;

        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "목표가 아직 확정되지 않았습니다."));
        BigDecimal monthlySavingAmount = computeMonthlySavingAmount(status, baselineAmount, effectiveChoice);
        BigDecimal productBaselineAmount = computeProductBaselineAmount(status, baselineAmount, effectiveChoice);

        // STEP 2. 새 구간월 대기 중이면 커밋하지 않는다 — 실제 커밋은 4-4에서 같은 choice를
        // 다시 실어 보낼 때 일어난다(§4-6).
        if (Boolean.TRUE.equals(status.pendingSegmentTransition())) {
            return new DeficitChoiceResult(monthlySavingAmount, productBaselineAmount, false);
        }

        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("로드맵이 없습니다."));
        RoadmapSegmentVO segment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));
        LocalDate thisMonth = YearMonth.now().atDay(1);

        // STEP 3. 멱등성 — 이번 달이 이미 커밋됐으면 재계산·재커밋 없이 그 값을 그대로 돌려준다.
        // 기준액은 그 행에 얼려둔 deficit_choice로 그때와 똑같이 재구성한다.
        Optional<MonthlySavingPlanVO> existing =
                monthlySavingPlanMapper.selectByRoadmapAndMonth(roadmap.getSavingsRoadmapId(), thisMonth);
        if (existing.isPresent()) {
            MonthlySavingPlanVO plan = existing.get();
            BigDecimal existingBasis = SPREAD.equals(plan.getDeficitChoice())
                    ? plan.getRequiredSnapshot() : plan.getBaselineSnapshot();
            return new DeficitChoiceResult(plan.getMonthlySavingAmount(), existingBasis, true);
        }

        // STEP 4. 실제 커밋 — 새 상품을 고르지 않는다. 이 구간이 시작될 때 이미 만들어진
        // NEW_SAVINGS 구독들(자기 스냅샷 금리·한도)에 다시 배분만 한다.
        List<ProductSubscriptionVO> activeSubscriptions =
                productSubscriptionMapper.selectActiveBySegment(segment.getSegmentId());
        List<ProductSubscriptionVO> activeSavings = activeSubscriptions.stream()
                .filter(s -> NEW_SAVINGS.equals(s.getSubscriptionRole()))
                .sorted(Comparator.comparing(ProductSubscriptionVO::getExpectedAppliedRate).reversed())
                .toList();
        List<ProductAllocationCandidate> candidates = activeSavings.stream()
                .map(s -> new ProductAllocationCandidate(
                        s.getProductId(), s.getExpectedAppliedRate(), s.getMonthlyPaymentLimitSnapshot()))
                .toList();
        Map<Long, Long> subscriptionIdByProductId = activeSavings.stream()
                .collect(Collectors.toMap(ProductSubscriptionVO::getProductId, ProductSubscriptionVO::getProductSubscriptionId));

        AllocationPlan plan = roadmapCalculationService.allocate(candidates, monthlySavingAmount);

        BigDecimal depositPrincipal = activeSubscriptions.stream()
                .filter(s -> ROLLOVER_DEPOSIT.equals(s.getSubscriptionRole()))
                .map(ProductSubscriptionVO::getInitialPrincipal)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal segmentSavingsPaid = transactionHistoryMapper.sumSavingsPaymentBySegment(segment.getSegmentId());
        BigDecimal cashSavingBalance = assetSnapshotMapper.selectLatest(roadmap.getSavingsRoadmapId())
                .map(AssetSnapshotVO::getCashSavingBalance)
                .orElse(BigDecimal.ZERO);
        BigDecimal currentAccumulatedFund = roadmapCalculationService.calculateCurrentAccumulatedFund(
                depositPrincipal, segmentSavingsPaid, cashSavingBalance);
        BigDecimal cumulativeSavingPerformance = calculateCumulativeSavingPerformanceNow(memberId, roadmap);

        MonthlySavingPlanVO newPlan = MonthlySavingPlanVO.builder()
                .savingsRoadmapId(roadmap.getSavingsRoadmapId())
                .segmentId(segment.getSegmentId())
                .planMonth(thisMonth)
                .cycleNo(status.cycleNo())
                .deficitChoice(effectiveChoice)
                .monthlySavingAmount(monthlySavingAmount)
                .recommendedCashSaving(plan.recommendedCashSaving())
                .currentAccumulatedFund(currentAccumulatedFund)
                .cumulativeSavingPerformance(cumulativeSavingPerformance)
                .baselineSnapshot(baselineAmount)
                .requiredSnapshot(monthlySavingAmount)
                .build();
        monthlySavingPlanMapper.insert(newPlan); // insert 후 monthlySavingPlanId 채워짐

        for (AllocationPlan.AllocationEntry entry : plan.allocations()) {
            MonthlySavingAllocationVO allocation = MonthlySavingAllocationVO.builder()
                    .monthlySavingPlanId(newPlan.getMonthlySavingPlanId())
                    .productSubscriptionId(subscriptionIdByProductId.get(entry.productId()))
                    .allocatedAmount(entry.allocatedAmount())
                    .allocationOrder(entry.allocationOrder())
                    .build();
            monthlySavingAllocationMapper.insert(allocation);
        }

        // STEP 5. 필요저축액 컬럼 갱신 — 설계 원칙 7, 이 트랜잭션이 이번 달 값을 확정하는 지점.
        goalMapper.updateMonthlyRequiredSaving(memberId, monthlySavingAmount);

        // STEP 6. projected_total_interest 갱신 — 4-6 응답 자체엔 이 필드가 없어 DB에만 채운다.
        // 4-4와 같은 이유로 insert 이후에 계산해야 한다(getGraph() 가 방금 넣은 이 행을
        // "가장 최근 회차"로 다시 조회해서 구성 기준액을 정하기 때문).
        RoadmapProjection projection = roadmapQueryService.getProjection(memberId);
        monthlySavingPlanMapper.updateProjectedTotalInterest(
                newPlan.getMonthlySavingPlanId(), projection.expectedInterestTotal());

        return new DeficitChoiceResult(monthlySavingAmount, productBaselineAmount, true);
    }

    // 구성 기준액(§4-7) — SPREAD면 필요저축액, 그 외(NONE/FULL_RECOVERY)면 목표기준액.
    // 4-4·4-6이 똑같이 쓰는 계산이라 공통으로 뺐다.
    private BigDecimal computeProductBaselineAmount(RoadmapStatus status, BigDecimal baselineAmount, String deficitChoice) {
        return SPREAD.equals(deficitChoice) ? status.requiredAmount() : baselineAmount;
    }

    // 당월저축액(§5-2) — FULL_RECOVERY면 목표기준액+부족액(한 번에 만회), 그 외면 필요저축액.
    private BigDecimal computeMonthlySavingAmount(RoadmapStatus status, BigDecimal baselineAmount, String deficitChoice) {
        return FULL_RECOVERY.equals(deficitChoice)
                ? baselineAmount.add(status.shortfallAmount() != null ? status.shortfallAmount() : BigDecimal.ZERO)
                : status.requiredAmount();
    }

    // 구독 하나의 만기 원금·세전이자 — 이자_계산식_결정.md 공식. DB(product_subscription.
    // maturity_amount)엔 이 둘을 합친 세전 금액을 그대로 저장하고, 실제 다음 구간으로 넘어가는
    // lumpSum은 이자 부분만 세후로 변환해서 더한다 — 만기 시 은행이 이자소득세를 원천징수하고
    // 남은 돈만 실제로 손에 들어와 재투자할 수 있기 때문이다(세전으로 굴리면 구간이 넘어갈
    // 때마다 실제보다 많은 돈이 들어가고, 구간이 여러 개면 그 차이가 복리로 누적된다).
    private record MaturityBreakdown(BigDecimal principal, BigDecimal preTaxInterest) {}

    private MaturityBreakdown calculateMaturityBreakdown(ProductSubscriptionVO subscription) {
        if (ROLLOVER_DEPOSIT.equals(subscription.getSubscriptionRole())) {
            BigDecimal interest = roadmapCalculationService.calculateDepositInterest(
                    subscription.getInitialPrincipal(), subscription.getExpectedAppliedRate(), subscription.getTermMonths());
            return new MaturityBreakdown(subscription.getInitialPrincipal(), interest);
        }
        List<SavingsPaymentRecord> payments =
                transactionHistoryMapper.selectSavingsPaymentsBySubscription(subscription.getProductSubscriptionId());
        BigDecimal principal = payments.stream().map(SavingsPaymentRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = roadmapCalculationService.calculateSavingsInterest(
                payments, subscription.getExpectedAppliedRate(), subscription.getMaturityDate());
        return new MaturityBreakdown(principal, interest);
    }

    // 로드맵 전체 기준 누적 저축실적(§2-5) — 지금 이 순간(구간 전환 커밋 시점) 기준으로 다시 계산한다.
    // RoadmapQueryService.getStatus() 의 STEP8과 같은 계산이지만, 그쪽은 RoadmapStatus 에
    // 이 중간값을 노출하지 않아서 이 트랜잭션에 얼려 저장할 값이 필요한 여기서 한 번 더 구한다.
    private BigDecimal calculateCumulativeSavingPerformanceNow(long memberId, SavingsRoadmapVO roadmap) {
        LocalDateTime roadmapStart = roadmap.getStartDate().atStartOfDay();
        LocalDateTime thisMonthStart = YearMonth.now().atDay(1).atStartOfDay();
        BigDecimal savingsPaymentSum = transactionHistoryMapper.sumSavingsPaymentBetween(memberId, roadmapStart, thisMonthStart);
        BigDecimal cashSavingBalance = assetSnapshotMapper.selectLatest(roadmap.getSavingsRoadmapId())
                .map(AssetSnapshotVO::getCashSavingBalance)
                .orElse(BigDecimal.ZERO);
        return roadmapCalculationService.calculateCumulativeSavingPerformance(savingsPaymentSum, cashSavingBalance);
    }

    // 우대조건 응답 중 will_meet=true 인 것만 반영해 상품별 우대금리 합을 구한다 (§4-3, §4-4).
    // 적금·예금 후보 모두 같은 로직이라 공통으로 뺐다.
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

    // 후보 상품 + 계산된 예상 적용금리를 함께 들고 다니기 위한 내부 전용 레코드.
    private record RatedCandidate(ProductRateCandidate product, BigDecimal appliedRate) {
    }
}
