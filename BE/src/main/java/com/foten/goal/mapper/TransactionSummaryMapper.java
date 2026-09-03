package com.foten.goal.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 누적저축실적(PDF 최종안) = 적금 실제 납입액 누계 + 직전월말 현금성 저축액
@Mapper
public interface TransactionSummaryMapper {

    // 적금 실제 납입액 누계: SUM(amount) WHERE transaction_type='SAVINGS_PAYMENT' AND transaction_at >= since
    BigDecimal findCumulativeSavingsPayment(@Param("memberId") Long memberId, @Param("since") LocalDateTime since);

    // asOf 시점 이전(포함) 마지막 거래의 balance_after — 계좌 잔액 스냅샷. 거래 이력이 없으면 empty
    Optional<BigDecimal> findBalanceAsOf(@Param("memberId") Long memberId, @Param("asOf") LocalDateTime asOf);

    // [windowStart, windowEnd) 구간의 월평균 EXPENSE 지출 (SUM / months)
    // TODO: FIXED+VARIABLE 전체 지출로 구현함. FIXED만 써야 하는지 팀 확인 필요.
    BigDecimal findAverageMonthlyExpense(
            @Param("memberId") Long memberId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd,
            @Param("months") int months);
}
