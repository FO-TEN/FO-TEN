package com.foten.ai.advisor;

import com.foten.ai.dto.ChatCard;
import com.foten.ai.llm.LlmMessage;

import java.util.ArrayList;
import java.util.List;

public record ChatContext(
        long memberId,
        String languageCode,
        String userMessage,
        List<LlmMessage> messages,
        List<String> calledTools,   // 이번 턴에 실제로 부른 툴 이름(선택지 근거)
        List<ChatCard> cards        // 이번 턴에 도구가 담은 카드. 답변에 딸려 나가고 이력에도 남는다
) {
    public static ChatContext of(long memberId, String languageCode, String userMessage) {
        return new ChatContext(memberId, languageCode, userMessage,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
