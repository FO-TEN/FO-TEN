package com.foten.spending.service;

import com.foten.spending.domain.CategoryTotal;
import com.foten.spending.domain.MonthlySpending;
import com.foten.spending.domain.SpendingCategory;
import com.foten.spending.domain.SpendingLine;
import com.foten.spending.mapper.SpendingSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class SpendingQueryServiceImpl implements SpendingQueryService{

    private static final String FIXED = "FIXED";
    private static final String VARIABLE = "VARIABLE";
    private static final int TOP_CATEGORIES_LIMIT = 3;
    private final SpendingSummaryMapper spendingSummaryMapper;

    @Override
    public MonthlySpending getMonthlySpending(long memberId, int monthsAgo) {
        List<SpendingLine> lines = spendingSummaryMapper.findByMonth(memberId, monthsAgo);
        YearMonth month = YearMonth.now().minusMonths(monthsAgo);

        Map<String, BigDecimal> fixed = byCategory(lines, FIXED);
        Map<String, BigDecimal> variable = byCategory(lines, VARIABLE);
        List<CategoryTotal> topCategories = topCategories(lines, TOP_CATEGORIES_LIMIT);

        return new MonthlySpending(
                month, daysCovered(month), sum(fixed), fixed, sum(variable), variable, topCategories);
    }

    // 이번 달은 오늘까지만 조회됨
    // 지난 달은 한 달 전체
    private int daysCovered(YearMonth month) {
        LocalDate today = LocalDate.now();
        return month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth();
    }

    // 0원은 담지 않는다.
    // 목록에 없는 카테고리는 뒤에 붙인다(순서유지)
    private Map<String, BigDecimal> byCategory(List<SpendingLine> lines, String expenseType) {
        Map<String, BigDecimal> amounts = new TreeMap<>();
        for (SpendingLine line : lines) {
            if (expenseType.equals(line.getExpenseType())) {
                amounts.merge(line.getCategory(), line.getAmount(), BigDecimal::add);
            }
        }

        Map<String, BigDecimal> ordered = new LinkedHashMap<>();
        for (String category : SpendingCategory.ALL) {
            put(ordered, category, amounts.get(category));
        }
        amounts.forEach((category, amount) -> put(ordered, category, amount));
        return ordered;
    }

    private void put(Map<String, BigDecimal> ordered, String category, BigDecimal amount) {
        if (amount != null && amount.signum() != 0) {
            ordered.putIfAbsent(category, amount);
        }
    }

    private BigDecimal sum(Map<String, BigDecimal> byCategory) {
        return byCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // FIXED+VARIABLE 구분 없이 카테고리별로 합산해 금액 내림차순 상위 limit개만 반환 (홈·소비내역 화면 전용).
    // byCategory()는 SpendingCategory.ALL 고정 순서로 정렬하는 표시용이라 "많은 순" 정렬 목적엔 안 맞아 따로 뺐다.
    private List<CategoryTotal> topCategories(List<SpendingLine> lines, int limit) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (SpendingLine line : lines) {
            amounts.merge(line.getCategory(), line.getAmount(), BigDecimal::add);
        }

        return amounts.entrySet().stream()
                .filter(e -> e.getValue().signum() != 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new CategoryTotal(e.getKey(), e.getValue()))
                .toList();
    }
}
