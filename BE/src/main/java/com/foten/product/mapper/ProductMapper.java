package com.foten.product.mapper;

import com.foten.product.domain.ProductRateCandidate;
import com.foten.product.domain.ProductVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {
    // 자유적립식 적금 중 이 가입기간(termMonths)에 기본금리가 정의된 상품 후보 (로직 v3 §4-2 필터).
    // 예금 후보 조회는 NEW_SEGMENT 분기에서 별도로 추가한다.
    List<ProductRateCandidate> selectSavingsCandidates(@Param("termMonths") int termMonths);

    // 구독에 물려있는 상품들의 상세정보 (구독 VO엔 productId만 있고 이름 등이 없어서). 호출 전
    // productIds 가 비어있지 않은지 확인한다 — 빈 리스트면 IN () 이 되어 SQL 오류가 난다.
    List<ProductVO> selectByIds(@Param("productIds") List<Long> productIds);
}
