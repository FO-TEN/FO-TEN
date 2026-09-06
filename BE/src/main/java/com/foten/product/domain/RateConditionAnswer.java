package com.foten.product.domain;

// POST /api/rate-conditions/responses 요청 바디를 옮겨 담는 도메인 입력 (DTO 아님, Controller 경계에서 변환).
public record RateConditionAnswer(String conditionCode, boolean willMeet) {
}
