package com.foten.product.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// transaction_history 는 product 도메인이 소유한 테이블이 아니지만(공용 원장),
// 이 도메인 전용 집계 쿼리를 여기서 직접 관리한다 — goal.TransactionSummaryMapper 와는 별개.
@Mapper
public interface TransactionHistoryMapper {

    // 로드맵 시작 이후 ~ 직전월까지의 실제 적금 납입액 누계 (§2-5 누적 저축실적)
    BigDecimal sumSavingsPaymentBetween(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until);

    // 특정 구간(segment)에 속한 적금 구독들의 실제 납입 합계 (§2-2 현재 적금 실제 납입금액)
    BigDecimal sumSavingsPaymentBySegment(@Param("segmentId") Long segmentId);
}
