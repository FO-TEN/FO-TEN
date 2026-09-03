package com.foten.goal.service;

import com.foten.goal.domain.CategorySpendingInput;
import com.foten.goal.domain.SavingCalculationOutput;
import java.util.ArrayList;
import java.util.List;

public class SavingCalculationServiceImpl implements SavingCalculationService {

    @Override
    public SavingCalculationOutput diagnose(
            int monthlyRequiredSaving,
            List<CategorySpendingInput> categories,
            int income,
            int remittance,
            int fixedCost,
            int currentTotalSpent) {

        int currentExpectedSaving = calcCurrentExpectedSaving(
                categories, income, remittance, fixedCost, currentTotalSpent);
        int maxExpectedSaving = calcMaxExpectedSaving(
                categories, income, remittance, fixedCost, currentTotalSpent);

        String judgeResult;
        String topCategory = null;
        int topAmount = 0;

        // 3단계 분기
        if (monthlyRequiredSaving > maxExpectedSaving) {
            judgeResult = "불가능";
        } else if (monthlyRequiredSaving > currentExpectedSaving) {
            judgeResult = "노력하면 가능";
            var top = pickTopCategory(categories);
            if (top != null) {
                topCategory = top.category();
                topAmount = top.amount();
            }
        } else {
            judgeResult = "여유있음";
        }

        int additionalNeeded = monthlyRequiredSaving - currentExpectedSaving;

        return new SavingCalculationOutput(
                currentExpectedSaving, maxExpectedSaving, topCategory, topAmount, judgeResult, additionalNeeded);
    }

    // 함수 1: 남은 예상 변동비 (현재예상저축액 구성요소)
    int calcExpectedRemaining(CategorySpendingInput c) {
        if (c.elapsedDays() < 7) {
            // 월초 방어: 최근 6개월 전체 평균 일평균으로 대체
            int totalSpent = c.last6MonthsSpending().stream().mapToInt(Integer::intValue).sum();
            int totalDaysAll = c.last6MonthsDays().stream().mapToInt(Integer::intValue).sum();
            if (totalDaysAll == 0) {
                // 0개월차(온보딩 직후, 6개월 이력 자체가 없음): 대체할 이력 데이터가 없음
                // TODO: 온보딩 예상지출 입력값 필드가 CategorySpendingInput에 추가되면 그 값으로 교체.
                return 0;
            }
            double dailyAvg = (double) totalSpent / totalDaysAll;
            return (int) Math.round(dailyAvg * (c.totalDays() - c.elapsedDays()));
        }
        double dailyAvg = (double) c.currentMonthSpent() / c.elapsedDays();
        int remainingDays = c.totalDays() - c.elapsedDays();
        return (int) Math.round(dailyAvg * remainingDays);
    }

    // 함수 2: 남은 조정 후 변동비 (최대예상저축액 구성요소, 하위 2번째 월 기준)
    int calcAdjustedRemaining(CategorySpendingInput c) {
        List<Integer> spending = c.last6MonthsSpending();
        List<Integer> days = c.last6MonthsDays();

        // 하위 2번째인지 확인
        if (spending.size() < 3) {
            // 데이터 부족 시 현재예상저축액 값으로 대체 (구간 분리 안 함)
            return calcExpectedRemaining(c);
        }

        // (지출액, 인덱스) 쌍으로 정렬해서 하위 2번째 인덱스 찾기
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < spending.size(); i++) indices.add(i);
        indices.sort((a, b) -> spending.get(a) - spending.get(b));
        int secondLowestIdx = indices.get(1);

        double adjustedDailyAvg = (double) spending.get(secondLowestIdx) / days.get(secondLowestIdx);
        int remainingDays = c.totalDays() - c.elapsedDays();
        return (int) Math.round(adjustedDailyAvg * remainingDays);
    }

    // 함수 3: MIN 안전장치 (역전 방지)
    int calcAdjustedRemainingSafe(CategorySpendingInput c) {
        return Math.min(calcExpectedRemaining(c), calcAdjustedRemaining(c));
    }

    // 함수 4: 카테고리별 절감 여력
    int calcSavingPotential(CategorySpendingInput c) {
        return calcExpectedRemaining(c) - calcAdjustedRemainingSafe(c);
    }

    // 함수 5: 현재예상저축액 (전체)
    int calcCurrentExpectedSaving(List<CategorySpendingInput> categories,
                                  int income, int remittance, int fixedCost, int currentTotalSpent) {
        int remainingExpected = categories.stream().mapToInt(this::calcExpectedRemaining).sum();
        return income - remittance - fixedCost - currentTotalSpent - remainingExpected;
    } // 이번 달이 끝났을 때 남을 돈

    // 함수 6: 최대예상저축액 (전체)
    int calcMaxExpectedSaving(List<CategorySpendingInput> categories,
                              int income, int remittance, int fixedCost, int currentTotalSpent) {
        int remainingAdjustedSafe = categories.stream().mapToInt(this::calcAdjustedRemainingSafe).sum();
        return income - remittance - fixedCost - currentTotalSpent - remainingAdjustedSafe;
    }

    // 함수 7: 절감 여력 1위 카테고리
    record TopCategory(String category, int amount) {}

    TopCategory pickTopCategory(List<CategorySpendingInput> categories) {
        return categories.stream()
                .map(c -> new TopCategory(c.category(), calcSavingPotential(c)))
                .max((a, b) -> a.amount() - b.amount())
                .orElse(null);
    }
}