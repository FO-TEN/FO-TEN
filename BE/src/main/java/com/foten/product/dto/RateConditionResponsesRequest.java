package com.foten.product.dto;

import java.util.List;

// deficitChoice: NEW_SEGMENT + hasShortfall=true 일 때만 필수(FULL_RECOVERY/SPREAD), 그 외엔 null.
public record RateConditionResponsesRequest(List<ResponseItem> responses, String deficitChoice) {
    public record ResponseItem(String conditionCode, boolean willMeet) {
    }
}
