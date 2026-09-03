package com.foten.ai.service;

import com.foten.ai.domain.ChatMessageVO;
import com.foten.ai.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMemoryImpl implements ChatMemory{
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT="ASSISTANT";
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatMessageVO> get(long memberId, int lastN) {
        List<ChatMessageVO> recent = new ArrayList<>(chatMessageMapper.findRecent(memberId, lastN));
        Collections.reverse(recent);

        // 만약 맨 앞의 대화가 ASSISTANT이면 지운다.(질문부터 시작되는 쌍이어야 함)
        if(!recent.isEmpty() && ROLE_ASSISTANT.equals(recent.get(0).getMessageRole())) {
            recent.remove(0);
        }
        return recent;
    }

    @Override
    public void addUserMessage(long memberId, String contentKo, String contentLocal, String languageCode) {
        chatMessageMapper.insert(ChatMessageVO.builder()
                    .memberId(memberId)
                    .messageRole(ROLE_USER)
                    .contentKo(contentKo)
                    .contentLocal(contentLocal)
                    .languageCode(languageCode)
                    .build());
    }

    @Override
    public void addAssistantMessage(long memberId, String contentKo, String contentLocal, String languageCode) {
        chatMessageMapper.insert(ChatMessageVO.builder()
                .memberId(memberId)
                .messageRole(ROLE_USER)
                .contentKo(contentKo)
                .contentLocal(contentLocal)
                .languageCode(languageCode)
                .build());
    }

    @Override
    public void clear(long memberId) {
        chatMessageMapper.deleteByMemberId(memberId);
    }
}
