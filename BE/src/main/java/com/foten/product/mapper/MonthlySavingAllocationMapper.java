package com.foten.product.mapper;

import com.foten.product.domain.MonthlySavingAllocationVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthlySavingAllocationMapper {
    void insert(MonthlySavingAllocationVO allocation);

    // 그 달 배분 내역 전체 — §4-9 "이번 달 배분 계획" 조회용. allocation_order(예상적용금리
    // 내림차순) 순으로 반환한다.
    List<MonthlySavingAllocationVO> selectByPlanId(@Param("monthlySavingPlanId") Long monthlySavingPlanId);
}
