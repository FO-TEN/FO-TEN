package com.foten.product.mapper;

import com.foten.product.domain.MonthlySavingAllocationVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonthlySavingAllocationMapper {
    void insert(MonthlySavingAllocationVO allocation);
}
