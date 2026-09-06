package com.foten.product.service;

import com.foten.common.RoadmapStateConflictException;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.StayInfo;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.StayInfoMapper;
import com.foten.product.domain.AllocationPlan;
import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.MemberRateConditionResponseVO;
import com.foten.product.domain.MonthlySavingAllocationVO;
import com.foten.product.domain.MonthlySavingPlanVO;
import com.foten.product.domain.ProductAllocationCandidate;
import com.foten.product.domain.ProductPreferentialRateVO;
import com.foten.product.domain.ProductRateCandidate;
import com.foten.product.domain.ProductSubscriptionVO;
import com.foten.product.domain.RateConditionAnswer;
import com.foten.product.domain.RoadmapSegmentVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SavingsRoadmapVO;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.mapper.MemberRateConditionResponseMapper;
import com.foten.product.mapper.MonthlySavingAllocationMapper;
import com.foten.product.mapper.MonthlySavingPlanMapper;
import com.foten.product.mapper.ProductMapper;
import com.foten.product.mapper.ProductPreferentialRateMapper;
import com.foten.product.mapper.ProductSubscriptionMapper;
import com.foten.product.mapper.RoadmapSegmentMapper;
import com.foten.product.mapper.SavingsRoadmapMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoadmapCommandServiceImpl implements RoadmapCommandService {

    private static final int FIRST_SEGMENT_NO = 1;
    private static final int FIRST_CYCLE_NO = 1;
    private static final String ONBOARDING = "ONBOARDING";
    private static final String NEW_SAVINGS = "NEW_SAVINGS";
    private static final String NO_DEFICIT = "NONE";

    private final SavingsRoadmapMapper savingsRoadmapMapper;
    private final RoadmapSegmentMapper roadmapSegmentMapper;
    private final MemberRateConditionResponseMapper memberRateConditionResponseMapper;
    private final ProductMapper productMapper;
    private final ProductPreferentialRateMapper productPreferentialRateMapper;
    private final ProductSubscriptionMapper productSubscriptionMapper;
    private final MonthlySavingPlanMapper monthlySavingPlanMapper;
    private final MonthlySavingAllocationMapper monthlySavingAllocationMapper;
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
                    "ROADMAP_ALREADY_EXISTS", "이미 로드맵이 있습니다. memberId=" + memberId);
        }

        // STEP 2. 목표기준액·체류정보는 이 도메인이 계산하지 않고 이미 확정돼 있어야 하는
        // 전제조건이다 (설계 원칙 7). 없으면 아직 온보딩이 끝나지 않은 것.
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "목표가 아직 확정되지 않았습니다. memberId=" + memberId));
        LocalDate expectedReturnDate = stayInfoMapper.selectByMemberId(memberId)
                .map(StayInfo::getExpectedReturnDate)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "체류 정보가 아직 확정되지 않았습니다. memberId=" + memberId));

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
    public SegmentComposition submitRateConditionResponses(long memberId, List<RateConditionAnswer> responses) {
        // STEP 1. 지금은 온보딩만 지원한다. NEW_SEGMENT 는 만기 이자 계산식이 아직 없어
        // REGULAR_MONTH 와 똑같이 막아둔다 — 별도 브랜치에서 채운다.
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        if (!ONBOARDING.equals(status.flowType())) {
            throw new RoadmapStateConflictException(
                    "NOT_APPLICABLE", "우대조건을 다시 확인할 시점이 아닙니다. flowType=" + status.flowType());
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

        SavingsRoadmapVO roadmap = savingsRoadmapMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("로드맵이 없습니다. memberId=" + memberId));
        RoadmapSegmentVO segment = roadmapSegmentMapper.selectActiveByRoadmapId(roadmap.getSavingsRoadmapId())
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중인 구간이 없습니다. savingsRoadmapId=" + roadmap.getSavingsRoadmapId()));
        BigDecimal baselineAmount = goalMapper.selectByMemberId(memberId)
                .map(Goal::getTargetBaselineAmount)
                .orElseThrow(() -> new RoadmapStateConflictException(
                        "GOAL_NOT_READY", "목표가 아직 확정되지 않았습니다. memberId=" + memberId));

        // STEP 3. 이 구간 기간(planned_months)에 맞는 자유적립식 적금 후보 조회 (§4-2).
        List<ProductRateCandidate> candidates = productMapper.selectSavingsCandidates(segment.getPlannedMonths());

        // STEP 4. 우대금리 합산 — 이번에 will_meet=true 로 응답한 조건만 반영한다 (§4-3, §4-4).
        Map<String, Boolean> willMeetByCondition = responses.stream()
                .collect(Collectors.toMap(RateConditionAnswer::conditionCode, RateConditionAnswer::willMeet));
        Map<Long, BigDecimal> bonusByProductId = new HashMap<>();
        if (!candidates.isEmpty()) {
            List<Long> productIds = candidates.stream().map(ProductRateCandidate::productId).toList();
            for (ProductPreferentialRateVO rate : productPreferentialRateMapper.selectByProductIds(productIds)) {
                if (Boolean.TRUE.equals(willMeetByCondition.get(rate.getConditionCode()))) {
                    bonusByProductId.merge(rate.getProductId(), rate.getRateBonus(), BigDecimal::add);
                }
            }
        }

        // STEP 5. 상품별 예상 적용금리 계산 후 내림차순 정렬 (§4-4, §4-5).
        List<RatedCandidate> rankedCandidates = candidates.stream()
                .map(c -> new RatedCandidate(c, roadmapCalculationService.calculateExpectedAppliedRate(
                        c.maxRate(), c.baseRate(), bonusByProductId.getOrDefault(c.productId(), BigDecimal.ZERO))))
                .sorted(Comparator.comparing(RatedCandidate::appliedRate).reversed())
                .toList();
        List<ProductAllocationCandidate> allocationCandidates = rankedCandidates.stream()
                .map(rc -> new ProductAllocationCandidate(
                        rc.product().productId(), rc.appliedRate(), rc.product().monthlyPaymentLimit()))
                .toList();

        // STEP 6. 목표기준액을 상품별로 배분 (§4-6/§5-3). ONBOARDING 이라 기준액=목표기준액.
        AllocationPlan plan = roadmapCalculationService.allocate(allocationCandidates, baselineAmount);

        // STEP 7. 배분된 상품마다 구독(NEW_SAVINGS) 생성.
        Map<Long, RatedCandidate> rankedById = rankedCandidates.stream()
                .collect(Collectors.toMap(rc -> rc.product().productId(), rc -> rc));
        Map<Long, Long> subscriptionIdByProductId = new HashMap<>();
        List<SegmentComposition.SavingsSummary> savingsSummaries = new ArrayList<>();

        for (AllocationPlan.AllocationEntry entry : plan.allocations()) {
            RatedCandidate ranked = rankedById.get(entry.productId());
            ProductSubscriptionVO subscription = ProductSubscriptionVO.builder()
                    .memberId(memberId)
                    .productId(entry.productId())
                    .segmentId(segment.getSegmentId())
                    .subscriptionRole(NEW_SAVINGS)
                    .termMonths(segment.getPlannedMonths())
                    .startDate(segment.getStartDate())
                    .maturityDate(segment.getEndDate())
                    .expectedAppliedRate(ranked.appliedRate())
                    .monthlyPaymentLimitSnapshot(ranked.product().monthlyPaymentLimit())
                    .build();
            productSubscriptionMapper.insert(subscription); // insert 후 productSubscriptionId 채워짐
            subscriptionIdByProductId.put(entry.productId(), subscription.getProductSubscriptionId());

            savingsSummaries.add(new SegmentComposition.SavingsSummary(
                    ranked.product().productName(), segment.getPlannedMonths(), ranked.appliedRate(),
                    ranked.product().monthlyPaymentLimit(), entry.allocatedAmount()));
        }

        // STEP 8. 이번 달(cycle_no=1) 저축 제안 생성. 목돈 없는 온보딩이라 누적값은 전부 0.
        MonthlySavingPlanVO monthlySavingPlan = MonthlySavingPlanVO.builder()
                .savingsRoadmapId(roadmap.getSavingsRoadmapId())
                .segmentId(segment.getSegmentId())
                .planMonth(YearMonth.from(segment.getStartDate()).atDay(1))
                .cycleNo(FIRST_CYCLE_NO)
                .deficitChoice(NO_DEFICIT)
                .monthlySavingAmount(baselineAmount)
                .recommendedCashSaving(plan.recommendedCashSaving())
                .currentAccumulatedFund(BigDecimal.ZERO)
                .cumulativeSavingPerformance(BigDecimal.ZERO)
                .baselineSnapshot(baselineAmount)
                .requiredSnapshot(baselineAmount)
                .build();
        monthlySavingPlanMapper.insert(monthlySavingPlan); // insert 후 monthlySavingPlanId 채워짐

        for (AllocationPlan.AllocationEntry entry : plan.allocations()) {
            MonthlySavingAllocationVO allocation = MonthlySavingAllocationVO.builder()
                    .monthlySavingPlanId(monthlySavingPlan.getMonthlySavingPlanId())
                    .productSubscriptionId(subscriptionIdByProductId.get(entry.productId()))
                    .allocatedAmount(entry.allocatedAmount())
                    .allocationOrder(entry.allocationOrder())
                    .build();
            monthlySavingAllocationMapper.insert(allocation);
        }

        // STEP 9. 필요저축액 컬럼 갱신 — 이 트랜잭션이 이번 달 필요저축액을 확정하는 유일한 지점
        // (설계 원칙 7). target_baseline_amount 는 여기서도 건드리지 않는다.
        goalMapper.updateMonthlyRequiredSaving(memberId, baselineAmount);

        // deposit/rolloverAmount 는 목돈이 없는 온보딩이라 항상 null. expectedInterestTotal/achievementRate 는
        // 만기 이자 계산식이 확정 전이라 항상 null (§15 추후 확정, NEW_SEGMENT 브랜치에서 함께 채운다).
        return new SegmentComposition(
                baselineAmount, null, null, savingsSummaries, plan.recommendedCashSaving(), null, null);
    }

    // 후보 상품 + 계산된 예상 적용금리를 함께 들고 다니기 위한 내부 전용 레코드.
    private record RatedCandidate(ProductRateCandidate product, BigDecimal appliedRate) {
    }
}
