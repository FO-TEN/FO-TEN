package com.foten.goal.service;

import com.foten.goal.domain.CategorySavingPotential;
import com.foten.goal.domain.CategorySpendingInput;
import com.foten.goal.domain.SavingCalculationOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    // calcExpectedRemaining=150000, calcAdjustedRemainingSafe=min(150000,90000)=90000 → 절감여력 60000원
    private static CategorySpendingInput 쇼핑() {
        return new CategorySpendingInput(
                "쇼핑", 150000, 15, 30,
                List.of(150000, 180000, 200000, 210000, 220000, 230000),
                List.of(30, 30, 30, 30, 30, 30));
    }

    // calcExpectedRemaining=90000, calcAdjustedRemainingSafe=min(90000,100000)=90000 → 절감여력 0원
    private static CategorySpendingInput 교통() {
        return new CategorySpendingInput(
                "교통", 90000, 15, 30,
                List.of(150000, 200000, 210000, 220000, 230000, 240000),
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
        // additionalNeeded = 900,000 - 1,010,000 = -110,000 (여유있음 만큼 음수)
        assertEquals(-110_000, result.additionalNeeded());
    }

    @Test
    void diagnose_필요저축액이_현재와_최대예상저축액_사이면_노력하면가능_추천카테고리는_식비() {
        SavingCalculationOutput result = service.diagnose(
                1_030_000, List.of(식비()), 2_500_000, 800_000, 300_000, 300_000);

        assertEquals("노력하면 가능", result.judgeResult());
        assertEquals("식비", result.topSavingCategory());
        assertEquals(47500, result.topSavingAmount());
        // additionalNeeded = 1,030,000 - 1,010,000 = 20,000
        assertEquals(20_000, result.additionalNeeded());
    }

    @Test
    void diagnose_필요저축액이_최대예상저축액보다_크면_불가능() {
        SavingCalculationOutput result = service.diagnose(
                1_100_000, List.of(식비()), 2_500_000, 800_000, 300_000, 300_000);

        assertEquals("불가능", result.judgeResult());
        // additionalNeeded = 1,100,000 - 1,010,000 = 90,000
        assertEquals(90_000, result.additionalNeeded());
    }
    @Test
    void calcExpectedRemaining_경과일이_7일_미만이면_이번달_데이터_대신_6개월_전체평균을_쓴다() {
        CategorySpendingInput c = new CategorySpendingInput(
                "식비", 15000, 3, 30,
                List.of(100000, 95000, 80000, 120000, 90000, 85000),
                List.of(30, 30, 30, 30, 30, 30));

        int result = service.calcExpectedRemaining(c);

        assertEquals(85500, result);
    }

    @Test
    void savingByCategory_절감여력이_0이하인_항목은_목록에서_제외된다() {
        List<CategorySavingPotential> result = service.savingByCategory(List.of(식비(), 교통()));

        assertEquals(1, result.size());
        assertEquals("식비", result.get(0).category());
    }

    @Test
    void savingByCategory_절감여력이_큰_순으로_내림차순_정렬된다() {
        List<CategorySavingPotential> result = service.savingByCategory(List.of(식비(), 쇼핑()));

        assertEquals(
                List.of(new CategorySavingPotential("쇼핑", 60000), new CategorySavingPotential("식비", 47500)),
                result);
    }

    @Test
    void calculateMonthlySavingAmount_FULL_RECOVERY면_이번_회차까지_밀린_전액을_만회한다() {
        // 로직 최종안 §5-2 예시: 목표기준액 100만원, cycleNo=2, 누적저축실적 70만원 → 130만원
        BigDecimal result = service.calculateMonthlySavingAmount(
                "FULL_RECOVERY", BigDecimal.valueOf(1_000_000), 2, BigDecimal.valueOf(700_000),
                BigDecimal.valueOf(1_005_000));

        assertEquals(0, BigDecimal.valueOf(1_300_000).compareTo(result));
    }

    @Test
    void calculateMonthlySavingAmount_SPREAD이면_필요저축액을_그대로_반환한다() {
        BigDecimal result = service.calculateMonthlySavingAmount(
                "SPREAD", BigDecimal.valueOf(1_000_000), 2, BigDecimal.valueOf(700_000),
                BigDecimal.valueOf(1_005_000));

        assertEquals(0, BigDecimal.valueOf(1_005_000).compareTo(result));
    }

    @Test
    void calculateMonthlySavingAmount_NONE이면_SPREAD와_동일하게_필요저축액을_그대로_반환한다() {
        BigDecimal result = service.calculateMonthlySavingAmount(
                "NONE", BigDecimal.valueOf(1_000_000), 2, BigDecimal.valueOf(700_000),
                BigDecimal.valueOf(1_005_000));

        assertEquals(0, BigDecimal.valueOf(1_005_000).compareTo(result));
    }

    @Test
    void calcExpectedRemaining_0개월차_회원은_6개월_이력이_없어_NaN_대신_0을_반환한다() {
        CategorySpendingInput c = new CategorySpendingInput(
                "식비", 0, 2, 30,
                List.of(),
                List.of());

        int result = service.calcExpectedRemaining(c);

        assertEquals(0, result);
    }

    // ===== 이슈 #84 재현 테스트: 신규회원 소비여력분석이 항상 낙관적으로 나오는 문제 =====
    //
    // 원인: elapsedDays는 GoalDiagnosisServiceImpl.buildCategorySpendingInputs()에서
    // LocalDate.now().getDayOfMonth()(이번 달 "달력 경과일")로 채워지는데, 이는 회원이
    // 실제로 추적된 기간(가입 후 실지출 발생 일수)과 무관하다. calcExpectedRemaining()의
    // dailyAvg = currentMonthSpent / elapsedDays 계산에서, 신규회원은 분자(실제 지출 발생
    // 일수만큼의 지출액)는 그대로인데 분모(달력 경과일)만 크게 잡혀 일평균이 실제보다
    // 축소된다 — 월말에 가까울수록(늦게 가입할수록) 왜곡이 커진다. 6개월 이력이 없는
    // 신규회원은 last6MonthsSpending도 전부 0으로 패딩되어(GoalDiagnosisServiceImpl.java:148,156)
    // calcAdjustedRemaining까지 같은 방향(과소 지출 추정)으로 밀린다.
    //
    // 아래 테스트들은 "현재의 (버그가 있는) 동작"을 문서화하는 재현 테스트다. 실제 수정은
    // 원인 규명 후 별도 작업이며, 그때 이 테스트들의 기대값도 함께 바뀌어야 한다.

    @Test
    void calcExpectedRemaining_신규회원은_경과일이_실제_추적기간보다_커서_일평균이_과소평가된다() {
        // "월 20일에 가입해 5일간 하루 5만원씩(총 25만원) 지출한" 신규회원을 흉내낸 입력 —
        // elapsedDays에는 실제 추적일(5일)이 아니라 달력 경과일(25일)이 들어간다
        // (GoalDiagnosisServiceImpl이 실제로 만들어내는 것과 동일한 형태).
        CategorySpendingInput c = new CategorySpendingInput(
                "식비", 250_000, 25, 30,
                List.of(0, 0, 0, 0, 0, 0),
                List.of(30, 30, 30, 30, 30, 30));

        int result = service.calcExpectedRemaining(c);

        // 실제 일평균은 250,000/5(실제 추적일)=50,000원이라 남은 5일간 250,000원이 더
        // 나가야 정상이지만, 버그로 인해 250,000/25(달력 경과일)=10,000원으로 계산돼
        // 남은 지출을 실제의 5분의 1인 50,000원으로 과소평가한다.
        assertEquals(50_000, result);
    }

    @Test
    void diagnose_신규회원은_실제_소비습관대로면_불가능에_가까워도_여유있음으로_나온다() {
        // 위와 같은 신규회원 입력(하루 5만원 소비 습관, 6개월 이력 없음).
        CategorySpendingInput 신규회원_식비 = new CategorySpendingInput(
                "식비", 250_000, 25, 30,
                List.of(0, 0, 0, 0, 0, 0),
                List.of(30, 30, 30, 30, 30, 30));

        // 실제 소비 습관(하루 5만원)이 남은 5일간 유지된다면 currentExpectedSaving은
        // 2,500,000-800,000-300,000-250,000-250,000=900,000원이라, 목표 1,000,000원은
        // "여유있음"이 아니어야 정상이다. 하지만 elapsedDays 왜곡 때문에 남은 지출을
        // 50,000원으로만 잡아 currentExpectedSaving이 1,100,000원으로 과대평가된다.
        SavingCalculationOutput result = service.diagnose(
                1_000_000, List.of(신규회원_식비), 2_500_000, 800_000, 300_000, 250_000);

        assertEquals("여유있음", result.judgeResult());
        assertEquals(-100_000, result.additionalNeeded());
    }
}
