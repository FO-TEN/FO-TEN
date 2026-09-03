package com.foten.ai.service;

import com.foten.ai.llm.LlmClient;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.prompt.SystemPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final LlmClient llmClient;

    public String reply(long memberId, String message) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(SystemPrompt.BASE));
        messages.add(LlmMessage.user(message));

        return llmClient.call(messages);
    }
}
