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
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private final GoalMapper goalMapper;
    private final FinancialInfoMapper financialInfoMapper;
    private final SpendingMapper spendingMapper;
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

        return new GoalDiagnosisResponse(
                goal.getTargetBaselineAmount(),
                goal.getMonthlyRequiredSaving(),
                BigDecimal.valueOf(result.additionalNeeded()),
                null, // cumulativeShortfall — 실제누적저축액 계산 로직 미구현
                null, // achievementRate — 위와 동일 이유
                null, // catchUpMode — 담당자 미정
                result.judgeResult());
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
