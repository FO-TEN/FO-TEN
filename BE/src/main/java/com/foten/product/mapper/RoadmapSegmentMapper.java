package com.foten.product.mapper;

import com.foten.product.domain.RoadmapSegmentVO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoadmapSegmentMapper {
    // 현재 진행 중인 구간 (status='ACTIVE'). 구조상 로드맵당 항상 최대 1건.
    Optional<RoadmapSegmentVO> selectActiveByRoadmapId(@Param("savingsRoadmapId") Long savingsRoadmapId);

    // 실제로 시작된 구간 전체(COMPLETED+ACTIVE), segment_no 순 — §4-7 그래프의 과거·현재
    // 부분. 미래 구간은 저장하지 않으므로 여기 안 나오고, Service 에서 계산해 이어붙인다.
    List<RoadmapSegmentVO> selectAllByRoadmapId(@Param("savingsRoadmapId") Long savingsRoadmapId);

    // 생성된 segment_id 를 segment.segmentId 에 다시 채워 넣는다.
    void insert(RoadmapSegmentVO segment);

    // 구간 마감 (NEW_SEGMENT 전환 시 직전 구간을 COMPLETED 로 바꾼다).
    void complete(@Param("segmentId") Long segmentId);
}
