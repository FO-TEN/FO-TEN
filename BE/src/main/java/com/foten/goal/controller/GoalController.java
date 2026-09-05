package com.foten.goal.controller;

import com.foten.goal.dto.GoalDiagnosisResponse;
import com.foten.goal.service.GoalDiagnosisService;
import com.foten.member.support.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GoalController {
    private final GoalDiagnosisService goalDiagnosisService;

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/api/goals/current/diagnosis")
    public ResponseEntity<GoalDiagnosisResponse> diagnosis(@LoginMember long memberId) {
        return ResponseEntity.ok(goalDiagnosisService.diagnose(memberId));
    }
}
