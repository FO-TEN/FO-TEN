package com.foten.goal.mapper;

import com.foten.goal.domain.StayInfo;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StayInfoMapper {
    Optional<StayInfo> selectByMemberId(@Param("memberId") Long memberId);
}
