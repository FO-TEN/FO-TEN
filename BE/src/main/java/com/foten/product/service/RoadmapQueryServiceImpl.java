package com.foten.product.service;

import com.foten.common.ResourceNotFoundException;
import com.foten.goal.domain.Goal;
import com.foten.goal.mapper.GoalMapper;
import com.foten.product.domain.AssetSnapshotVO;
import com.foten.product.domain.MonthlySavingPlanVO;
import com.foten.product.domain.ProductSubscriptionVO;
import com.foten.product.domain.RoadmapSegmentVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SavingsRoadmapVO;
import com.foten.product.mapper.AssetSnapshotMapper;
import com.foten.product.mapper.MonthlySavingPlanMapper;
import com.foten.product.mapper.ProductSubscriptionMapper;
import com.foten.product.mapper.RoadmapSegmentMapper;
import com.foten.product.mapper.SavingsRoadmapMapper;
import com.foten.product.mapper.TransactionHistoryMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoadmapQueryServiceImpl implements RoadmapQueryService {

    private static final String ROLLOVER_DEPOSIT = "ROLLOVER_DEPOSIT";

    private final SavingsRoadmapMapper savingsRoadmapMapper;
    private final RoadmapSegmentMapper roadmapSegmentMapper;
    private final MonthlySavingPlanMapper monthlySavingPlanMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;
    private final ProductSubscriptionMapper productSubscriptionMapper;
    private final TransactionHistoryMapper transactionHistoryMapper;
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
                .orElseThrow(() -> new ResourceNotFoundException("목표 정보가 없습니다. memberId=" + memberId));

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

        // STEP 10. 결과 조립. rolloverAmount(지난 구간에서 모은 돈)는 구간을 실제로 마감하는 로직이
        // 아직 없어서 항상 null — 그 로직을 만들 때 함께 채운다.
        String flowType = pendingSegmentTransition ? "NEW_SEGMENT" : "REGULAR_MONTH";
        BigDecimal lastMonthActualAmount = latestSnapshot.map(AssetSnapshotVO::getMonthlyPayment).orElse(null);

        return new RoadmapStatus(
                true, flowType, cycleNo, segment.getSegmentNo(), segment.getIsLastSegment(),
                pendingSegmentTransition, lastMonthActualAmount, hasShortfall,
                hasShortfall ? shortfallAmount : null, null, baselineAmount, requiredAmount);
    }

    // §2-2 현재 누적자금의 "현재 예금 금액" 항목 — 이 구간의 ACTIVE ROLLOVER_DEPOSIT 구독 원금 합
    private BigDecimal sumActiveDepositPrincipal(Long segmentId) {
        return productSubscriptionMapper.selectActiveBySegment(segmentId).stream()
                .filter(s -> ROLLOVER_DEPOSIT.equals(s.getSubscriptionRole()))
                .map(ProductSubscriptionVO::getInitialPrincipal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
