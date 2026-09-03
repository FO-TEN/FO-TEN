package com.foten.ai.dto;

import com.foten.ai.domain.ChatMessageVO;

import java.time.LocalDateTime;

public record ChatMessageResponse (
        long chatMessageId,
        String messageRole,
        String contentKo,
        String ContentLocal,
        String languageCode,
        LocalDateTime createdAt
){
    public static ChatMessageResponse from(ChatMessageVO vo) {
        return new ChatMessageResponse(
                vo.getChatMessageId(),
                vo.getMessageRole(),
                vo.getContentKo(),
                vo.getContentLocal(),
                vo.getLanguageCode(),
                vo.getCreatedAt()
        );
    }
}
