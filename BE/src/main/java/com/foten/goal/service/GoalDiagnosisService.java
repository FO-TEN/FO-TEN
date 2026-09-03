package com.foten.goal.service;

import com.foten.goal.dto.GoalDiagnosisResponse;

public interface GoalDiagnosisService {
    GoalDiagnosisResponse diagnose(Long memberId);
}
