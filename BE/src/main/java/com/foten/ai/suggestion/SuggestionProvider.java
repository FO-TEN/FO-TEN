package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;

import java.util.List;

// 답변 아래에 붙일 선택지를 만든다
// 선택지 내용 -> 서버에 의해 결정(모델X)
public interface SuggestionProvider {
    List<Suggestion> suggest(ChatContext ctx, String contentKo);
}
