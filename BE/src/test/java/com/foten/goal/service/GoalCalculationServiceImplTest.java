package com.foten.goal.service;

import com.foten.goal.domain.GoalCalculationInput;
import com.foten.goal.domain.GoalCalculationOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalCalculationServiceImplTest {

    private final GoalCalculationServiceImpl service = new GoalCalculationServiceImpl();

    @Test
    void calculate_체류기간_4년10개월이면_가입월_해제월을_제외한_56개월로_나눈다() {
        GoalCalculationOutput result = service.calculate(new GoalCalculationInput(
                BigDecimal.valueOf(56_000_000),
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2030, 11, 1))); // 4년 10개월 = 58개월 - 가입월/해제월 2개월

        assertEquals(56, result.remainingMonths());
        assertEquals(BigDecimal.valueOf(1_000_000), result.targetBaselineAmount());
    }

    @Test
    void calculate_2개월을_제외하면_0이하가_되어도_0개월_개념을_쓰지_않고_최소_1개월로_계산한다() {
        GoalCalculationOutput result = service.calculate(new GoalCalculationInput(
                BigDecimal.valueOf(500_000),
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 15))); // 1개월 - 2개월 = -1 → 최소 1개월로 보정

        assertEquals(1, result.remainingMonths());
        assertEquals(BigDecimal.valueOf(500_000), result.targetBaselineAmount());
    }
}
