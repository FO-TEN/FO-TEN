package com.foten.goal.service;

import com.foten.goal.domain.CategorySpendingInput;
import com.foten.goal.domain.SavingCalculationOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavingCalculationServiceImplTest {

    private final SavingCalculationServiceImpl service = new SavingCalculationServiceImpl();

    private static CategorySpendingInput 식비() {
        return new CategorySpendingInput(
                "식비", 90000, 15, 30,
                List.of(100000, 95000, 80000, 120000, 90000, 85000),
                List.of(30, 30, 30, 30, 30, 30));
    }

    @Test
    void calcExpectedRemaining_식비_경과일_기준_일평균으로_남은일수를_추정한다() {
        int result = service.calcExpectedRemaining(식비());

        // 일평균 90000/15=6000원 * 남은 15일 = 90000원
        assertEquals(90000, result);
    }

    @Test
    void calcAdjustedRemaining_식비_하위2번째_달_기준으로_남은일수를_추정한다() {
        int result = service.calcAdjustedRemaining(식비());

        // 정렬: 80000 < 85000 < 90000 < 95000 < 100000 < 120000 → 하위 2번째 = 85000원
        // 일평균 85000/30≈2833.33원 * 남은 15일 = 42500원
        assertEquals(42500, result);
    }

    @Test
    void calcSavingPotential_식비_예상치와_조정치의_차이만큼_절감여력이다() {
        int result = service.calcSavingPotential(식비());

        // calcExpectedRemaining=90000, calcAdjustedRemainingSafe=min(90000,42500)=42500
        // 절감 여력 = 90000 - 42500 = 47500원
        assertEquals(47500, result);
    }

    // 공통: currentExpectedSaving=2500000-800000-300000-300000-90000=1,010,000
    //       maxExpectedSaving   =2500000-800000-300000-300000-42500=1,057,500

    @Test
    void diagnose_필요저축액이_현재예상저축액보다_작으면_여유있음() {
        SavingCalculationOutput result = service.diagnose(
                900_000, List.of(식비()), 2_500_000, 800_000, 300_000, 300_000);

        assertEquals("여유있음", result.judgeResult());
    }

    @Test
    void diagnose_필요저축액이_현재와_최대예상저축액_사이면_노력하면가능_추천카테고리는_식비() {
        SavingCalculationOutput result = service.diagnose(
                1_030_000, List.of(식비()), 2_500_000, 800_000, 300_000, 300_000);

        assertEquals("노력하면 가능", result.judgeResult());
        assertEquals("식비", result.topSavingCategory());
        assertEquals(47500, result.topSavingAmount());
    }

    @Test
    void diagnose_필요저축액이_최대예상저축액보다_크면_불가능() {
        SavingCalculationOutput result = service.diagnose(
                1_100_000, List.of(식비()), 2_500_000, 800_000, 300_000, 300_000);

        assertEquals("불가능", result.judgeResult());
    }
}
