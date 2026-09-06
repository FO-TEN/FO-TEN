package com.foten.product.service;

import com.foten.product.domain.CreatedRoadmap;

// 로드맵 도메인의 쓰기 전용 Service. 읽기 전용인 RoadmapQueryService 와 분리한다
// (.claude/rules/backend.md — @Transactional 은 Service 에만, 쓰기와 읽기를 섞지 않는다).
public interface RoadmapCommandService {
    CreatedRoadmap createRoadmap(long memberId);
}
