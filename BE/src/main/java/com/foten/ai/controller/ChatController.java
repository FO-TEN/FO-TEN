package com.foten.ai.controller;

import com.foten.ai.dto.ChatReply;
import com.foten.ai.dto.ChatRequest;
import com.foten.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController {
    // 로그인이 없어서 seed data 1번 user로 고정
    private static final long TEMP_MEMBER_ID = 1L;
    private final ChatService chatService;

    @PostMapping("/api/chat")
    public ResponseEntity<ChatReply> chat(@RequestBody ChatRequest request) {
        if(request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String content = chatService.reply(TEMP_MEMBER_ID, request.message());
        return ResponseEntity.ok(new ChatReply(content));
    }
}
