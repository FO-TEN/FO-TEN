package com.foten.goal.mapper;

import com.foten.goal.domain.FinancialInfo;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinancialInfoMapper {
    Optional<FinancialInfo> selectByMemberId(@Param("memberId") Long memberId);
}
