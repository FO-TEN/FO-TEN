package com.foten.product.mapper;

import com.foten.product.domain.SavingsRoadmapVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SavingsRoadmapMapper {
    Optional<SavingsRoadmapVO> selectByMemberId(@Param("memberId") Long memberId);

    // 생성된 savings_roadmap_id 를 roadmap.savingsRoadmapId 에 다시 채워 넣는다.
    void insert(SavingsRoadmapVO roadmap);
}
