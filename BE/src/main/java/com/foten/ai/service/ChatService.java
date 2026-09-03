package com.foten.ai.service;

import com.foten.ai.advisor.Advisor;
import com.foten.ai.advisor.AdvisorChain;
import com.foten.ai.advisor.ChatContext;
import com.foten.ai.llm.LlmClient;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.prompt.SystemPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final LlmClient llmClient;
    private final List<Advisor> advisors;

    public String reply(long memberId, String message) {
        ChatContext ctx = ChatContext.of(memberId, null, message);
        ctx.messages().add(LlmMessage.system(SystemPrompt.BASE));

        return AdvisorChain.of(advisors, c-> llmClient.call(c.messages())).next(ctx);
    }
}
