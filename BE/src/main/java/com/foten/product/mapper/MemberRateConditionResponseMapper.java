package com.foten.product.mapper;

import com.foten.product.domain.MemberRateConditionResponseVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberRateConditionResponseMapper {
    // 최신 응답만 유지한다 — 재질문 시 UPSERT 로 덮어쓴다 (로직 v3 §4-3).
    void upsertAll(@Param("responses") List<MemberRateConditionResponseVO> responses);

    // 이 회원이 저장해둔 우대조건 응답 전체 — §4-7 그래프의 미래 구간 우대금리 계산은 새
    // 응답을 받는 게 아니라 이미 저장된 값을 그대로 쓴다(재질문 아님).
    List<MemberRateConditionResponseVO> selectByMemberId(@Param("memberId") Long memberId);
}
