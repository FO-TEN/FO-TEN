package com.foten.goal.service;

import com.foten.goal.domain.GoalCalculationInput;
import com.foten.goal.domain.GoalCalculationOutput;

public interface GoalCalculationService {
    // 필요저축액 : (목표저축액 - 현재자산) / 남은개월수
    GoalCalculationOutput calculate(GoalCalculationInput input);
}