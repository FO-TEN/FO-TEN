package com.foten.product.mapper;

import com.foten.product.domain.SavingsRoadmapVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SavingsRoadmapMapper {
    Optional<SavingsRoadmapVO> selectByMemberId(@Param("memberId") Long memberId);
}
