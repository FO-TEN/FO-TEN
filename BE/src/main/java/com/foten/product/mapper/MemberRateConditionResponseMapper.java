package com.foten.product.mapper;

import com.foten.product.domain.MemberRateConditionResponseVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberRateConditionResponseMapper {
    // 최신 응답만 유지한다 — 재질문 시 UPSERT 로 덮어쓴다 (로직 v3 §4-3).
    void upsertAll(@Param("responses") List<MemberRateConditionResponseVO> responses);
}
