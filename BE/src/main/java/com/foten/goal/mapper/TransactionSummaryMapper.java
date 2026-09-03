package com.foten.goal.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransactionSummaryMapper {

    // since 이후 누적 실제저축액 = SUM(SALARY+OTHER_IN) - SUM(EXPENSE+REMITTANCE+OTHER_OUT)
    // SAVINGS_PAYMENT/DEPOSIT_PAYMENT/MATURITY_RECEIPT는 예적금 상품 간 이동일 뿐이라 제외
    BigDecimal findCumulativeNetSavings(@Param("memberId") Long memberId, @Param("since") LocalDateTime since);
}
