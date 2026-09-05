package com.foten.spending.service;

import com.foten.spending.domain.MonthlySpending;

public interface SpendingQueryService {
    // monthsAgo : 0->이번 달 / 1->지난 달 전체
    MonthlySpending getMonthlySpending(long memberId, int monthsAgo);
}
