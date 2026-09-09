package com.foten.product.service;

import com.foten.common.clock.DemoClock;
import com.foten.common.mapper.DemoClockMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foten.common.InvalidRequestException;
import com.foten.common.RoadmapStateConflictException;
import com.foten.goal.domain.Goal;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.StayInfoMapper;
import com.foten.product.domain.DeficitChoiceResult;
import com.foten.product.domain.MonthlySavingPlanVO;
import com.foten.product.domain.RoadmapSegmentVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SavingsRoadmapVO;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// confirmDeficitChoice()는 계산 헬퍼(RoadmapCalculationServiceImplTest)와 달리
// 지금까지 유닛테스트가 없던 메서드다. 분기가 많고(가드 3개 + 3가지 choice + 멱등성 +
// 새구간월 대기) 손으로 계정 여러 개 만들어 재현하기 어려운 영역이라 우선순위로 추가한다.
@ExtendWith(MockitoExtension.class)
class RoadmapCommandServiceImplTest {

    // demo_clock 행이 없으면(기본 Optional.empty) 실제 오늘을 쓴다 — 기존 테스트 전제 그대로.
    @Mock private DemoClockMapper demoClockMapper;

    private static final long MEMBER_ID = 1L;

    @Mock private SavingsRoadmapMapper savingsRoadmapMapper;
    @Mock private RoadmapSegmentMapper roadmapSegmentMapper;
    @Mock private MemberRateConditionResponseMapper memberRateConditionResponseMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductPreferentialRateMapper productPreferentialRateMapper;
    @Mock private ProductSubscriptionMapper productSubscriptionMapper;
    @Mock private MonthlySavingPlanMapper monthlySavingPlanMapper;
    @Mock private MonthlySavingAllocationMapper monthlySavingAllocationMapper;
    @Mock private AssetSnapshotMapper assetSnapshotMapper;
    @Mock private TransactionHistoryMapper transactionHistoryMapper;
    @Mock private GoalMapper goalMapper;
    @Mock private StayInfoMapper stayInfoMapper;
    @Mock private RoadmapQueryService roadmapQueryService;
    @Mock private RoadmapCalculationService roadmapCalculationService;

    private RoadmapCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoadmapCommandServiceImpl(
                savingsRoadmapMapper, roadmapSegmentMapper, memberRateConditionResponseMapper,
                productMapper, productPreferentialRateMapper, productSubscriptionMapper,
                monthlySavingPlanMapper, monthlySavingAllocationMapper, assetSnapshotMapper,
                transactionHistoryMapper, goalMapper, stayInfoMapper,
                roadmapQueryService, roadmapCalculationService, new DemoClock(demoClockMapper));
    }

    private static Goal 목표(BigDecimal baselineAmount) {
        return Goal.builder()
                .goalId(1L)
                .memberId(MEMBER_ID)
                .targetBaselineAmount(baselineAmount)
                .build();
    }

    // RoadmapStatus 필드 순서: roadmapExists, flowType, cycleNo, currentSegmentNo, isLastSegment,
    // pendingSegmentTransition, lastMonthActualAmount, hasShortfall, shortfallAmount, rolloverAmount,
    // baselineAmount, requiredAmount
    private static RoadmapStatus 상태(
            String flowType, boolean pendingSegmentTransition, boolean hasShortfall,
            BigDecimal shortfallAmount, BigDecimal baselineAmount, BigDecimal requiredAmount) {
        return new RoadmapStatus(
                true, flowType, 3, 1, false, pendingSegmentTransition,
                BigDecimal.valueOf(900_000), hasShortfall, shortfallAmount, null,
                baselineAmount, requiredAmount);
    }

    @Test
    void confirmDeficitChoice_로드맵이_없으면_NOT_APPLICABLE() {
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(RoadmapStatus.notOnboarded());

        RoadmapStateConflictException ex = assertThrows(RoadmapStateConflictException.class,
                () -> service.confirmDeficitChoice(MEMBER_ID, null));
        assertEquals("NOT_APPLICABLE", ex.getErrorCode());
    }

    @Test
    void confirmDeficitChoice_ONBOARDING_상태면_NOT_APPLICABLE() {
        RoadmapStatus onboarding = RoadmapStatus.onboarding(1, false, BigDecimal.valueOf(1_000_000));
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(onboarding);

        RoadmapStateConflictException ex = assertThrows(RoadmapStateConflictException.class,
                () -> service.confirmDeficitChoice(MEMBER_ID, null));
        assertEquals("NOT_APPLICABLE", ex.getErrorCode());
    }

    @Test
    void confirmDeficitChoice_부족액_있는데_choice가_null이면_DEFICIT_CHOICE_REQUIRED() {
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(
                상태("REGULAR_MONTH", false, true, BigDecimal.valueOf(300_000),
                        BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_005_000)));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> service.confirmDeficitChoice(MEMBER_ID, null));
        assertEquals("DEFICIT_CHOICE_REQUIRED", ex.getErrorCode());
    }

    @Test
    void confirmDeficitChoice_goal이_없으면_GOAL_NOT_READY() {
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(
                상태("REGULAR_MONTH", true, false, null,
                        BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_005_000)));
        when(goalMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        RoadmapStateConflictException ex = assertThrows(RoadmapStateConflictException.class,
                () -> service.confirmDeficitChoice(MEMBER_ID, "FULL_RECOVERY"));
        assertEquals("GOAL_NOT_READY", ex.getErrorCode());
    }

    @Test
    void confirmDeficitChoice_부족액_없는데_choice가_와도_무시하고_NONE으로_취급한다() {
        // hasShortfall=false인데 choice="FULL_RECOVERY"를 보내도 무시되고 NONE 취급되어야 한다.
        // FULL_RECOVERY로 실제 처리됐다면 shortfall을 더했겠지만, 여기선 필요저축액 그대로 나와야 함.
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(
                상태("REGULAR_MONTH", true, false, null,
                        BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_005_000)));
        when(goalMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000))));

        DeficitChoiceResult result = service.confirmDeficitChoice(MEMBER_ID, "FULL_RECOVERY");

        assertEquals(0, BigDecimal.valueOf(1_005_000).compareTo(result.monthlySavingAmount()));
        assertFalse(result.committed());
    }

    @Test
    void confirmDeficitChoice_FULL_RECOVERY이고_새구간월_대기중이면_계산만하고_커밋하지_않는다() {
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(
                상태("REGULAR_MONTH", true, true, BigDecimal.valueOf(300_000),
                        BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_005_000)));
        when(goalMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000))));

        DeficitChoiceResult result = service.confirmDeficitChoice(MEMBER_ID, "FULL_RECOVERY");

        // 당월저축액 = 목표기준액 100만 + 부족액 30만 = 130만
        assertEquals(0, BigDecimal.valueOf(1_300_000).compareTo(result.monthlySavingAmount()));
        // 구성기준액 = SPREAD가 아니므로 목표기준액 그대로
        assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(result.productBaselineAmount()));
        assertFalse(result.committed());
        verify(monthlySavingPlanMapper, never()).insert(any());
        verify(goalMapper, never()).updateMonthlyRequiredSaving(anyLong(), any());
    }

    @Test
    void confirmDeficitChoice_SPREAD이고_새구간월_대기중이면_구성기준액이_필요저축액이다() {
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(
                상태("REGULAR_MONTH", true, true, BigDecimal.valueOf(300_000),
                        BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_005_000)));
        when(goalMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000))));

        DeficitChoiceResult result = service.confirmDeficitChoice(MEMBER_ID, "SPREAD");

        assertEquals(0, BigDecimal.valueOf(1_005_000).compareTo(result.monthlySavingAmount()));
        assertEquals(0, BigDecimal.valueOf(1_005_000).compareTo(result.productBaselineAmount()));
        assertFalse(result.committed());
    }

    @Test
    void confirmDeficitChoice_이번달_이미_커밋됐으면_재계산없이_기존값을_그대로_반환한다() {
        when(roadmapQueryService.getStatus(MEMBER_ID)).thenReturn(
                상태("REGULAR_MONTH", false, true, BigDecimal.valueOf(300_000),
                        BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_005_000)));
        when(goalMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000))));

        SavingsRoadmapVO roadmap = SavingsRoadmapVO.builder().savingsRoadmapId(10L).memberId(MEMBER_ID).build();
        when(savingsRoadmapMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(roadmap));

        RoadmapSegmentVO segment = RoadmapSegmentVO.builder().segmentId(100L).savingsRoadmapId(10L).build();
        when(roadmapSegmentMapper.selectActiveByRoadmapId(10L)).thenReturn(Optional.of(segment));

        MonthlySavingPlanVO existingPlan = MonthlySavingPlanVO.builder()
                .monthlySavingAmount(BigDecimal.valueOf(1_300_000))
                .deficitChoice("FULL_RECOVERY")
                .baselineSnapshot(BigDecimal.valueOf(1_000_000))
                .requiredSnapshot(BigDecimal.valueOf(1_005_000))
                .build();
        when(monthlySavingPlanMapper.selectByRoadmapAndMonth(eq(10L), any()))
                .thenReturn(Optional.of(existingPlan));

        DeficitChoiceResult result = service.confirmDeficitChoice(MEMBER_ID, "FULL_RECOVERY");

        assertEquals(0, BigDecimal.valueOf(1_300_000).compareTo(result.monthlySavingAmount()));
        assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(result.productBaselineAmount()));
        assertTrue(result.committed());
        verify(monthlySavingPlanMapper, never()).insert(any());
        verify(goalMapper, never()).updateMonthlyRequiredSaving(anyLong(), any());
    }
}
