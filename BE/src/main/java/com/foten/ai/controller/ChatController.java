package com.foten.ai.controller;

import com.foten.ai.dto.ChatMessageResponse;
import com.foten.ai.dto.ChatReply;
import com.foten.ai.dto.ChatRequest;
import com.foten.ai.service.ChatMemory;
import com.foten.ai.service.ChatService;
import com.foten.member.support.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    // LLM 은 글자 수만큼 돈이 나간다. 상담 질문이 이보다 길 이유가 없다.
    // 막지 않으면 붙여넣은 본문이 그대로 LLM 으로 가고, chat_message 에 남아 다음 턴마다 딸려 간다.
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatService chatService;
    private final ChatMemory chatMemory;

    @PostMapping("/api/chat")
    public ResponseEntity<ChatReply> chat(@LoginMember long memberId, @RequestBody ChatRequest request) {
        if(request == null || request.message() == null || request.message().isBlank()
                || request.message().length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(chatService.reply(memberId, request.message()));
    }

    @GetMapping("/api/chat/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @LoginMember long memberId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = (size == null) ? DEFAULT_PAGE_SIZE : size;
        if(pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().build();
        }

        List<ChatMessageResponse> messages = chatMemory.getPage(memberId, before, pageSize)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        return ResponseEntity.ok(messages);
    }
}
