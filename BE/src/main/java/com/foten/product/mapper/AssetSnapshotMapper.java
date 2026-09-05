package com.foten.product.mapper;

import com.foten.product.domain.AssetSnapshotVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetSnapshotMapper {
    // 가장 최근 마감월 스냅샷. 로드맵 생성 직후(마감월 없음)엔 empty.
    Optional<AssetSnapshotVO> selectLatest(@Param("savingsRoadmapId") Long savingsRoadmapId);
}
