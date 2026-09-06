package com.foten.ai.tool;

import com.foten.ai.dto.ChatCard;

import java.util.List;

// cards 는 ChatContext 의 목록을 그대로 받는다. 도구는 문자열만 돌려주므로 카드는 이 통로로 올린다.
public record ToolContext(long memberId, String languageCode, List<ChatCard> cards) {
}
