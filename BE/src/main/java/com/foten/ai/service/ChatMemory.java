package com.foten.ai.service;

import com.foten.ai.domain.ChatMessageVO;

import java.util.List;

public interface ChatMemory {

    // 오래된 것 -> 최신 순
    // lastN : 짝수->질문 & 응답 한 쌍으로 가져오기 위함
    List<ChatMessageVO> get(long memberId, int lastN);

    void addUserMessage(long memberId, String contentKo, String contentLocal, String languageCode);

    void addAssistantMessage(long memberId, String contentKo, String contentLocal, String languageCode);

    void clear(long memberId);  // 테스트 관리용
}
