package com.foten.product.controller;

import com.foten.product.domain.RoadmapStatus;
import com.foten.product.dto.RoadmapStatusResponse;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoadmapController {

    // 로그인이 없어서 seed data 1번 user로 고정 (GoalController/ChatController와 동일 패턴)
    private static final long TEMP_MEMBER_ID = 1L;

    private final RoadmapQueryService roadmapQueryService;

    @GetMapping("/api/roadmap/status")
    public ResponseEntity<RoadmapStatusResponse> status() {
        RoadmapStatus status = roadmapQueryService.getStatus(TEMP_MEMBER_ID);
        return ResponseEntity.ok(RoadmapStatusResponse.from(status));
    }
}
