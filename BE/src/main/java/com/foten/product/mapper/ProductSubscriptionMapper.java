package com.foten.product.mapper;

import com.foten.product.domain.ProductSubscriptionVO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductSubscriptionMapper {
    // 이 구간에서 아직 만기되지 않은(status='ACTIVE') 구독 전체 — 적금·예금 구분 없이 반환.
    // 적금이 여러 개일 수 있어(§4-6 배분 결과) 이 구간 전체를 순회하며 하나씩 만기 처리한다.
    List<ProductSubscriptionVO> selectActiveBySegment(@Param("segmentId") Long segmentId);

    // 생성된 product_subscription_id 를 subscription.productSubscriptionId 에 다시 채워 넣는다.
    void insert(ProductSubscriptionVO subscription);

    // 만기 처리 (NEW_SEGMENT 전환 시 직전 구간 구독을 닫을 때) — 이자_계산식_결정.md 공식으로
    // 계산한 maturityAmount(세전, 원금+이자 합산)를 채우고 status 를 MATURED 로 바꾼다.
    void matureAndClose(@Param("productSubscriptionId") Long productSubscriptionId,
                         @Param("maturityAmount") BigDecimal maturityAmount);
}
