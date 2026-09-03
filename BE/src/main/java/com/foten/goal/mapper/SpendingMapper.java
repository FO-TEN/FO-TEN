package com.foten.goal.mapper;

import com.foten.goal.domain.CategoryMonthlySpending;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpendingMapper {

    // 이번 달 포함 최근 monthsBack개월, 카테고리별 월별 변동비(VARIABLE) 지출 합계
    List<CategoryMonthlySpending> findCategoryMonthlySpending(
            @Param("memberId") Long memberId, @Param("monthsBack") int monthsBack);

    // 이번 달 현재까지의 변동비(VARIABLE) 지출 합계 전체 (카테고리 무관)
    BigDecimal findCurrentTotalSpent(@Param("memberId") Long memberId);
}
