package com.foten.product.dto;

import com.foten.product.domain.RateConditionVO;

public record RateConditionResponse(String conditionCode, String label, String description) {
    public static RateConditionResponse from(RateConditionVO vo) {
        return new RateConditionResponse(vo.getConditionCode(), vo.getLabel(), vo.getDescription());
    }
}
