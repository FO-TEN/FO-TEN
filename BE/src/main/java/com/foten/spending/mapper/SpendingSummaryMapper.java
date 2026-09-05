package com.foten.spending.mapper;

import com.foten.spending.domain.SpendingLine;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SpendingSummaryMapper {
    List<SpendingLine> findByMonth(
            @Param("memberId") long memberId,
            @Param("monthsAgo") int monthsAgo
    );
}
