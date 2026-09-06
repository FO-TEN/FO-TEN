package com.foten.product.mapper;

import com.foten.product.domain.ProductPreferentialRateVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductPreferentialRateMapper {
    // 호출 전 productIds 가 비어있지 않은지 확인한다 — 빈 리스트면 IN () 이 되어 SQL 오류가 난다.
    List<ProductPreferentialRateVO> selectByProductIds(@Param("productIds") List<Long> productIds);
}
