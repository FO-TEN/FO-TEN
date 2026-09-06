package com.foten.product.service;

import com.foten.common.RoadmapStateConflictException;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.StayInfo;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.StayInfoMapper;
import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.FirstSegmentPlan;
import com.foten.product.domain.RoadmapSegmentVO;
import com.foten.product.domain.SavingsRoadmapVO;
import com.foten.product.mapper.RoadmapSegmentMapper;
import com.foten.product.mapper.SavingsRoadmapMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoadmapCommandServiceImpl implements RoadmapCommandService {

    private static final int FIRST_SEGMENT_NO = 1;

    private final SavingsRoadmapMapper savingsRoadmapMapper;
    private final RoadmapSegmentMapper roadmapSegmentMapper;
    private final GoalMapper goalMapper; // 교차 도메인, 읽기 전용
    private final StayInfoMapper stayInfoMapper; // 교차 도메인, 읽기 전용
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
}
