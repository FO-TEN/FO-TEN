package com.foten.product.service;

import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.DeficitChoiceResult;
import com.foten.product.domain.RateConditionAnswer;
import com.foten.product.domain.SegmentComposition;
import java.util.List;

// 로드맵 도메인의 쓰기 전용 Service. 읽기 전용인 RoadmapQueryService 와 분리한다
// (.claude/rules/backend.md — @Transactional 은 Service 에만, 쓰기와 읽기를 섞지 않는다).
public interface RoadmapCommandService {
    CreatedRoadmap createRoadmap(long memberId);

    // 우대조건 응답 제출 + 즉시 커밋. ONBOARDING·NEW_SEGMENT 둘 다 지원한다.
    // deficitChoice 는 NEW_SEGMENT+부족액 있을 때만 필수(FULL_RECOVERY/SPREAD), 그 외엔 null.
    SegmentComposition submitRateConditionResponses(long memberId, List<RateConditionAnswer> responses, String deficitChoice);

    // 당월 저축 방식 확정 (§4-6). ONBOARDING 에서는 호출하지 않는다(§3-1 흐름표에 없음).
    // choice 는 hasShortfall=true 일 때만 의미가 있다(FULL_RECOVERY/SPREAD), 그 외엔 null이거나
    // 와도 무시된다. REGULAR_MONTH 면 즉시 커밋, 새구간월 대기 중이면 계산만 하고 커밋 안 함.
    DeficitChoiceResult confirmDeficitChoice(long memberId, String choice);
}
