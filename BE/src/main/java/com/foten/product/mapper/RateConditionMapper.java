package com.foten.product.mapper;

import com.foten.product.domain.RateConditionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RateConditionMapper {
    // 상품 추천 전 사용자에게 던지는 공통 질문 목록 (로직 v3 §4-3)
    List<RateConditionVO> selectBehaviorBased();
}
