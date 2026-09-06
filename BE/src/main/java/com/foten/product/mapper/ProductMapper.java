package com.foten.product.mapper;

import com.foten.product.domain.ProductRateCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {
    // 자유적립식 적금 중 이 가입기간(termMonths)에 기본금리가 정의된 상품 후보 (로직 v3 §4-2 필터).
    // 예금 후보 조회는 NEW_SEGMENT 분기에서 별도로 추가한다.
    List<ProductRateCandidate> selectSavingsCandidates(@Param("termMonths") int termMonths);
}
