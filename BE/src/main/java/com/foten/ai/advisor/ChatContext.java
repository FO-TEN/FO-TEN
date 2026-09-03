package com.foten.ai.advisor;

import com.foten.ai.llm.LlmMessage;

import java.util.ArrayList;
import java.util.List;

public record ChatContext(
        long memberId,
        String languageCode,
        String userMessage,
        List<LlmMessage> messages
) {
    public static ChatContext of(long memberId, String languageCode, String userMessage) {
        return new ChatContext(memberId, languageCode, userMessage, new ArrayList<>());
    }
}
