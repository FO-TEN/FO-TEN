package com.foten.goal.controller;

import com.foten.goal.dto.GoalDiagnosisResponse;
import com.foten.goal.service.GoalDiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GoalController {

    // 로그인이 없어서 seed data 1번 user로 고정 (ai/ChatController와 동일 패턴)
    private static final long TEMP_MEMBER_ID = 1L;

    private final GoalDiagnosisService goalDiagnosisService;

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/api/goals/current/diagnosis")
    public ResponseEntity<GoalDiagnosisResponse> diagnosis() {
        return ResponseEntity.ok(goalDiagnosisService.diagnose(TEMP_MEMBER_ID));
    }
}
