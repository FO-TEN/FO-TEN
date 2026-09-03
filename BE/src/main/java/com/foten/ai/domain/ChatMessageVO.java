package com.foten.ai.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageVO {
    private Long chatMessageId;
    private Long memberId;
    private String messageRole;
    private String contentKo;
    private String contentLocal;
    private String languageCode;
    private LocalDateTime createdAt;
}
