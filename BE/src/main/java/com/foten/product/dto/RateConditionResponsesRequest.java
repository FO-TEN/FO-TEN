package com.foten.product.dto;

import java.util.List;

public record RateConditionResponsesRequest(List<ResponseItem> responses) {
    public record ResponseItem(String conditionCode, boolean willMeet) {
    }
}
