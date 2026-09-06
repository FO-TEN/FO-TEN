package com.foten.product.mapper;

import com.foten.product.domain.RoadmapSegmentVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoadmapSegmentMapper {
    // 현재 진행 중인 구간 (status='ACTIVE'). 구조상 로드맵당 항상 최대 1건.
    Optional<RoadmapSegmentVO> selectActiveByRoadmapId(@Param("savingsRoadmapId") Long savingsRoadmapId);
}
