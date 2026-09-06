package com.foten.ai.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

// 답변에 딸려 나가는 카드(메시지의 필드)
public record ChatCard(String type, @JsonRawValue String payload) {

    public static ChatCard of(String type, String payload) {
        return (type == null || payload == null) ? null : new ChatCard(type, payload);
    }
}
