package com.foten.ai.service;

import com.foten.ai.domain.ChatMessageVO;
import com.foten.ai.dto.ChatCard;

import java.util.List;

public interface ChatMemory {

    // 오래된 것 -> 최신 순
    // lastN : 짝수->질문 & 응답 한 쌍으로 가져오기 위함
    List<ChatMessageVO> get(long memberId, int lastN);

    List<ChatMessageVO> getPage(long memberId, Long before, int size);

    void addUserMessage(long memberId, String contentKo, String contentLocal, String languageCode);

    // card 는 답변에 딸린 그림이다. 없으면 null.
    void addAssistantMessage(long memberId, String contentKo, String contentLocal,
                             String languageCode, ChatCard card);

    void clear(long memberId);  // 테스트 관리용
}
