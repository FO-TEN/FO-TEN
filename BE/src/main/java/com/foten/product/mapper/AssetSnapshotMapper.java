package com.foten.product.mapper;

import com.foten.product.domain.AssetSnapshotVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetSnapshotMapper {
    // 가장 최근 마감월 스냅샷. 로드맵 생성 직후(마감월 없음)엔 empty.
    Optional<AssetSnapshotVO> selectLatest(@Param("savingsRoadmapId") Long savingsRoadmapId);

    // 그 구간의 마지막 마감월 스냅샷 — §4-7 그래프에서 완료/현재 구간 각각의 "그 구간 자체"
    // cashAmount 를 구할 때 쓴다(selectLatest 는 로드맵 전체 기준이라 구간이 여러 개면 안 맞음).
    Optional<AssetSnapshotVO> selectLatestBySegment(@Param("segmentId") Long segmentId);
}
