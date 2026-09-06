package com.foten.ai.dto;

import java.util.List;

public record ChatReply(String contentKo, String contentLocal, ChatCard card,
                       List<Suggestion> suggestions) {
}