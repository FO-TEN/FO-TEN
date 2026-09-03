package com.foten.goal.service;

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

    @Override
    public GoalDiagnosisResponse diagnose(Long memberId) {
        Goal goal = goalMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("목표 정보가 없습니다. memberId=" + memberId));
        FinancialInfo financialInfo = financialInfoMapper.selectByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("재무 정보가 없습니다. memberId=" + memberId));

        List<CategorySpendingInput> categories = buildCategorySpendingInputs(memberId);
        int currentTotalSpent = spendingMapper.findCurrentTotalSpent(memberId).intValue();

        SavingCalculationOutput result = savingCalculationService.diagnose(
                goal.getTargetBaselineAmount().intValue(),
                categories,
                financialInfo.getMonthlyIncome().intValue(),
                financialInfo.getMonthlyRemittance().intValue(),
                financialInfo.getMonthlyLivingCost().intValue(),
                currentTotalSpent);

        int elapsedMonths = calcElapsedMonths(goal.getCreatedAt());
        BigDecimal cumulativeTarget = goal.getTargetBaselineAmount().multiply(BigDecimal.valueOf(elapsedMonths));
        BigDecimal actualCumulativeSavings =
                transactionSummaryMapper.findCumulativeNetSavings(memberId, goal.getCreatedAt());
        BigDecimal cumulativeShortfall = cumulativeTarget.subtract(actualCumulativeSavings);
        BigDecimal achievementRate = calcAchievementRate(actualCumulativeSavings, cumulativeTarget);

        return new GoalDiagnosisResponse(
                goal.getTargetBaselineAmount(),
                goal.getMonthlyRequiredSaving(),
                BigDecimal.valueOf(result.additionalNeeded()),
                cumulativeShortfall,
                achievementRate,
                null, // catchUpMode — 담당자 미정
                result.judgeResult());
    }

    // goal 생성 시점(목표기준액 스냅샷 시점)부터 오늘까지 경과한 개월 수. 최소 1개월.
    private int calcElapsedMonths(LocalDateTime goalCreatedAt) {
        Period period = Period.between(goalCreatedAt.toLocalDate(), LocalDate.now());
        int totalMonths = period.getYears() * 12 + period.getMonths();
        return Math.max(totalMonths, 1);
    }

    // 실제누적저축액 / (목표기준액 x 경과개월) x 100. 분모가 0 이하인 비정상 케이스만 방어.
    private BigDecimal calcAchievementRate(BigDecimal actualCumulativeSavings, BigDecimal cumulativeTarget) {
        if (cumulativeTarget.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return actualCumulativeSavings
                .divide(cumulativeTarget, PERCENTAGE_SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    // transaction_history 집계 로우(카테고리 x 월)를 카테고리별 CategorySpendingInput 으로 조립
    private List<CategorySpendingInput> buildCategorySpendingInputs(Long memberId) {
        List<CategoryMonthlySpending> rows =
                spendingMapper.findCategoryMonthlySpending(memberId, HISTORY_MONTHS + 1);

        YearMonth currentYearMonth = YearMonth.now();
        int elapsedDays = LocalDate.now().getDayOfMonth();
        int totalDays = currentYearMonth.lengthOfMonth();

        Map<String, Map<String, BigDecimal>> spendingByCategory = rows.stream()
                .collect(Collectors.groupingBy(
                        CategoryMonthlySpending::getCategory,
                        Collectors.toMap(CategoryMonthlySpending::getYearMonth, CategoryMonthlySpending::getAmount)));

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
