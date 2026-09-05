package com.foten.common;

import lombok.Getter;

// 로드맵 도메인 상태 충돌 (예: 목표 미확정 상태에서 로드맵 생성 시도, 이미 있는 로드맵 재생성 시도).
// 여러 상황을 한 클래스로 묶되 errorCode 로 구분한다 — API 명세서 §6 에러 케이스 참고.
@Getter
public class RoadmapStateConflictException extends RuntimeException {
    private final String errorCode; // 예: GOAL_NOT_READY, ROADMAP_ALREADY_EXISTS

    public RoadmapStateConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
