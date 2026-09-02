package com.foten.goal.domain;

import java.util.List;

public record CategorySpendingInput(
        String category,
        int currentMonthSpent,      // 이번 달 현재까지 지출
        int elapsedDays,            // 이번 달 경과일
        int totalDays,              // 이번 달 총일수
        List<Integer> last6MonthsSpending,  // 최근 6개월 지출 (오래된→최근)
        List<Integer> last6MonthsDays       // 각 달 총일수 (같은 순서)
) {
}