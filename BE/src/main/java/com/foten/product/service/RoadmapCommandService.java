package com.foten.product.service;

import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.RateConditionAnswer;
import com.foten.product.domain.SegmentComposition;
import java.util.List;

// 로드맵 도메인의 쓰기 전용 Service. 읽기 전용인 RoadmapQueryService 와 분리한다
// (.claude/rules/backend.md — @Transactional 은 Service 에만, 쓰기와 읽기를 섞지 않는다).
public interface RoadmapCommandService {
    CreatedRoadmap createRoadmap(long memberId);

    // 우대조건 응답 제출 + 즉시 커밋. 지금은 flowType=ONBOARDING 만 지원한다 —
    // NEW_SEGMENT 는 만기 이자 계산식이 아직 확정 전이라 별도 브랜치로 미뤘다.
    SegmentComposition submitRateConditionResponses(long memberId, List<RateConditionAnswer> responses);
}
