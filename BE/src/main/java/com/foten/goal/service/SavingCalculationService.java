package com.foten.goal.service;

import com.foten.goal.domain.CategorySpendingInput;
import com.foten.goal.domain.SavingCalculationOutput;
import java.util.List;

public interface SavingCalculationService {
 // 필요저축액과 현재예상저축액/최대예상저축액을 비교해 3구간으로 판정
    SavingCalculationOutput diagnose(
            int requiredMonthlySaving,   // 필요저축액 (goal.required_monthly_saving)
            List<CategorySpendingInput> categories,
            int income,
            int remittance,
            int fixedCost,
            int currentTotalSpent
    );
}