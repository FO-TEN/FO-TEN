package com.foten.product.mapper;

import com.foten.product.domain.ProductSubscriptionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductSubscriptionMapper {
    // 이 구간에서 아직 만기되지 않은(status='ACTIVE') 구독 전체 — 적금·예금 구분 없이 반환.
    List<ProductSubscriptionVO> selectActiveBySegment(@Param("segmentId") Long segmentId);
}
