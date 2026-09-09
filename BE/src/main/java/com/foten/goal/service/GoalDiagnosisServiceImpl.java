package com.foten.goal.service;

import com.foten.common.clock.DemoClock;
import com.foten.common.ResourceNotFoundException;
import com.foten.goal.domain.CategoryMonthlySpending;
import com.foten.goal.domain.CategorySpendingInput;
import com.foten.goal.domain.FinancialInfo;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.SavingCalculationOutput;
import com.foten.goal.dto.GoalDiagnosisResponse;
import com.foten.goal.mapper.FinancialInfoMapper;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.SpendingMapper;
import com.foten.goal.mapper.TransactionSummaryMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoalDiagnosisServiceImpl implements GoalDiagnosisService {

    private static final int HISTORY_MONTHS = 6;
    private static final int PERCENTAGE_SCALE = 1;

    private final GoalMapper goalMapper;
    private final FinancialInfoMapper financialInfoMapper;
    private final SpendingMapper spendingMapper;
    private final TransactionSummaryMapper transactionSummaryMapper;
    private final SavingCalculationService savingCalculationService;
    private final DemoClock demoClock;

    @Override
    public GoalDiagnosisResponse diagnose(Long memberId) {
        Goal goal = goalMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("목표 정보가 없습니다."));
        FinancialInfo financialInfo = financialInfoMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("재무 정보가 없습니다."));
        LocalDate today = demoClock.today(memberId);

        List<CategorySpendingInput> categories = buildCategorySpendingInputs(memberId, today);
        int currentTotalSpent = spendingMapper.findCurrentTotalSpent(memberId).intValue();

        SavingCalculationOutput result = savingCalculationService.diagnose(
                goal.getTargetBaselineAmount().intValue(),
                categories,
                financialInfo.getMonthlyIncome().intValue(),
                financialInfo.getMonthlyRemittance().intValue(),
                financialInfo.getMonthlyLivingCost().intValue(),
                currentTotalSpent);

        int elapsedMonths = calcElapsedMonths(goal.getCreatedAt(), today);
        BigDecimal cumulativeTarget = goal.getTargetBaselineAmount().multiply(BigDecimal.valueOf(elapsedMonths));
        BigDecimal actualCumulativeSavings =
                calcActualCumulativeSavings(memberId, goal.getCreatedAt(), today).setScale(0, RoundingMode.HALF_UP);
        // KRW는 소수점 없는 정수 단위(스키마 전체가 DECIMAL(n,0))인데, 평균 계산 과정에서
        // SQL 나눗셈으로 늘어난 스케일이 그대로 남아있어 명시적으로 0자리로 맞춘다.
        BigDecimal cumulativeShortfall =
                cumulativeTarget.subtract(actualCumulativeSavings);
        BigDecimal achievementRate = calcAchievementRate(
                financialInfo.getCurrentSavings(), actualCumulativeSavings, goal.getTargetAmountKrw());

        return new GoalDiagnosisResponse(
                goal.getTargetBaselineAmount(),
                goal.getMonthlyRequiredSaving(),
                BigDecimal.valueOf(result.additionalNeeded()),
                cumulativeShortfall,
                achievementRate,
                null, // deficitChoice — 선택 기록은 AI 대화창 담당. monthly_saving_plan 생성 후 연결
                result.judgeResult(),
                BigDecimal.valueOf(result.currentExpectedSaving()),
                BigDecimal.valueOf(result.maxExpectedSaving()),
                result.topSavingCategory(),
                BigDecimal.valueOf(result.topSavingAmount()),
                elapsedMonths,
                cumulativeTarget,
                actualCumulativeSavings,
                result.savingByCategory());
    }

    // goal 생성 시점(목표기준액 스냅샷 시점)부터 오늘까지 경과한 개월 수. 최소 1개월.
    private int calcElapsedMonths(LocalDateTime goalCreatedAt, LocalDate today) {
        Period period = Period.between(goalCreatedAt.toLocalDate(), today);
        int totalMonths = period.getYears() * 12 + period.getMonths();
        return Math.max(totalMonths, 1);
    }

    // 누적저축실적(PDF 최종안) = 적금 실제 납입액 누계 + 현금성 저축 순증분(직전월말 잔액 -
    // goal 생성 시점 잔액). goal 생성 이전부터 있던 돈이 섞여 들어가지 않도록 생성 시점
    // 잔액을 기준선으로 뺀다.
    private BigDecimal calcActualCumulativeSavings(Long memberId, LocalDateTime goalCreatedAt, LocalDate today) {
        BigDecimal cumulativeSavingsPayment =
                transactionSummaryMapper.findCumulativeSavingsPayment(memberId, goalCreatedAt);

        YearMonth currentMonth = YearMonth.from(today);
        LocalDateTime endOfPreviousMonth = currentMonth.minusMonths(1).atEndOfMonth().atTime(LocalTime.MAX);
        BigDecimal balanceAsOfLastMonth =
                transactionSummaryMapper.findBalanceAsOf(memberId, endOfPreviousMonth).orElse(BigDecimal.ZERO);
        BigDecimal balanceAtGoalCreation =
                transactionSummaryMapper.findBalanceAsOf(memberId, goalCreatedAt).orElse(BigDecimal.ZERO);

        BigDecimal cashSavings = balanceAsOfLastMonth.subtract(balanceAtGoalCreation).max(BigDecimal.ZERO);

        return cumulativeSavingsPayment.add(cashSavings);
    }

    // (financial_info.current_savings + 실제누적저축액) / target_amount_krw x 100.
    // 분모가 0 이하인 비정상 케이스만 방어.
    private BigDecimal calcAchievementRate(
            BigDecimal currentSavings, BigDecimal actualCumulativeSavings, BigDecimal targetAmountKrw) {
        if (targetAmountKrw.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return currentSavings.add(actualCumulativeSavings)
                .divide(targetAmountKrw, PERCENTAGE_SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    // transaction_history 집계 로우(카테고리 x 월)를 카테고리별 CategorySpendingInput 으로 조립
    private List<CategorySpendingInput> buildCategorySpendingInputs(Long memberId, LocalDate today) {
        List<CategoryMonthlySpending> rows =
                spendingMapper.findCategoryMonthlySpending(memberId, HISTORY_MONTHS + 1);

        YearMonth currentYearMonth = YearMonth.from(today);
        int elapsedDays = today.getDayOfMonth();
        int totalDays = currentYearMonth.lengthOfMonth();

        Map<String, Map<String, BigDecimal>> spendingByCategory = rows.stream()
                .collect(Collectors.groupingBy(
                        CategoryMonthlySpending::getCategory,
                        Collectors.toMap(CategoryMonthlySpending::getSpendingMonth, CategoryMonthlySpending::getAmount)));

        List<CategorySpendingInput> categories = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : spendingByCategory.entrySet()) {
            String category = entry.getKey();
            Map<String, BigDecimal> monthlyAmounts = entry.getValue();

            int currentMonthSpent = monthlyAmounts
                    .getOrDefault(currentYearMonth.toString(), BigDecimal.ZERO)
                    .intValue();

            List<Integer> last6MonthsSpending = new ArrayList<>();
            List<Integer> last6MonthsDays = new ArrayList<>();
            for (int i = HISTORY_MONTHS; i >= 1; i--) {
                YearMonth targetMonth = currentYearMonth.minusMonths(i);
                last6MonthsSpending.add(
                        monthlyAmounts.getOrDefault(targetMonth.toString(), BigDecimal.ZERO).intValue());
                last6MonthsDays.add(targetMonth.lengthOfMonth());
            }

            categories.add(new CategorySpendingInput(
                    category, currentMonthSpent, elapsedDays, totalDays, last6MonthsSpending, last6MonthsDays));
        }
        return categories;
    }
}
