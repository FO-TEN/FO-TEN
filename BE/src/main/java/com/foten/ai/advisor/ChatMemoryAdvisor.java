package com.foten.ai.advisor;

import com.foten.ai.domain.ChatMessageVO;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.service.ChatMemory;
import com.foten.ai.service.ChatMemoryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class ChatMemoryAdvisor implements Advisor {
    private static final int WINDOW = 10;
    private final ChatMemory chatMemory;

    @Override
    public String around(ChatContext ctx, AdvisorChain chain) {
        for(ChatMessageVO vo : chatMemory.get(ctx.memberId(), WINDOW)) {
            ctx.messages().add(toLlmMessage(vo));
        }
        ctx.messages().add(LlmMessage.user(ctx.userMessage()));

        return chain.next(ctx);
    }

    // context에는 한 언어만 삽입
    // 사용자 입력은 번역하지 않은 원문 사용
    private LlmMessage toLlmMessage(ChatMessageVO vo) {
        return ChatMemoryImpl.ROLE_USER.equals(vo.getMessageRole())
                ? LlmMessage.user(vo.getContentLocal())
                : LlmMessage.assistant(vo.getContentKo());
    }
}
