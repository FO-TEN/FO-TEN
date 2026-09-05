package com.foten.spending.service;

import com.foten.spending.domain.MonthlySpending;
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
    private static final List<String> CATEGORIES = List.of("식비", "교통", "통신", "쇼핑", "기타");
    private final SpendingSummaryMapper spendingSummaryMapper;

    @Override
    public MonthlySpending getMonthlySpending(long memberId, int monthsAgo) {
        List<SpendingLine> lines = spendingSummaryMapper.findByMonth(memberId, monthsAgo);
        YearMonth month = YearMonth.now().minusMonths(monthsAgo);

        Map<String, BigDecimal> fixed = byCategory(lines, FIXED);
        Map<String, BigDecimal> variable = byCategory(lines, VARIABLE);

        return new MonthlySpending(month, daysCovered(month), sum(fixed), fixed, sum(variable), variable);
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
        for (String category : CATEGORIES) {
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
}
