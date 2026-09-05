package com.foten.ai.service;

import com.foten.ai.advisor.Advisor;
import com.foten.ai.advisor.AdvisorChain;
import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.ChatReply;
import com.foten.ai.dto.Suggestion;
import com.foten.ai.llm.LlmChatResponse;
import com.foten.ai.llm.LlmClient;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.llm.LlmToolCall;
import com.foten.ai.mapper.MemberLanguageMapper;
import com.foten.ai.prompt.SystemPrompt;
import com.foten.ai.suggestion.SuggestionProvider;
import com.foten.ai.tool.ToolContext;
import com.foten.ai.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private static final int MAX_TOOL_ROUNDS = 3;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final List<Advisor> advisors;
    private final MemberLanguageMapper memberLanguageMapper;
    private final Translator translator;
    private final ChatMemory chatMemory;
    private final List<SuggestionProvider> suggestionProviders;

    public ChatReply reply(long memberId, String message) {
        String languageCode = memberLanguageMapper.findLanguageCode(memberId);

        ChatContext ctx = ChatContext.of(memberId, languageCode, message);
        ctx.messages().add(LlmMessage.system(SystemPrompt.BASE));

        String contentKo = AdvisorChain.of(advisors, this::runToolLoop).next(ctx);
        String contentLocal = translator.translate(contentKo, languageCode);

        chatMemory.addUserMessage(memberId, null, message, languageCode);
        chatMemory.addAssistantMessage(memberId, contentKo, contentLocal, languageCode);

        return new ChatReply(contentKo, contentLocal, suggestions(ctx, contentKo, languageCode));
    }

    private String runToolLoop(ChatContext ctx) {
        ToolContext toolContext = new ToolContext(ctx.memberId(), ctx.languageCode());

        for(int round=0; round<MAX_TOOL_ROUNDS; round++) {
            LlmChatResponse.Choice choice = llmClient.callWithTools(ctx.messages(), toolRegistry.toLlmTools());
            List<LlmToolCall> toolCalls = choice.message().toolCalls();

            if(toolCalls == null || toolCalls.isEmpty()) {
                return choice.message().content();
            }

            ctx.messages().add(LlmMessage.assistantToolCalls(toolCalls));

            for(LlmToolCall call : toolCalls) {
                ctx.calledTools().add(call.function().name());
                String result = toolRegistry.execute(
                        call.function().name(),
                        call.function().arguments(),
                        toolContext
                );
                ctx.messages().add(LlmMessage.tool(call.id(), result));
            }
        }

        log.warn("툴 왕복 상한({}) 도달 - 루프를 종료한다.", MAX_TOOL_ROUNDS);
        return "죄송해요, 지금은 답변을 정리하지 못했어요. 다시 물어봐 주시겠어요?";
    }

    // 선택지는 대화 이력에 남기지 않는다.
    // 누른 칩만 대화로 들어와 기록된다.
    private List<Suggestion> suggestions(ChatContext ctx, String contentKo, String languageCode) {

        List<Suggestion> chips = suggestionProviders.stream()
                .flatMap(provider -> provider.suggest(ctx, contentKo).stream())
                .toList();

        if (chips.isEmpty()) {
            return chips;
        }

        List<String> labels = translator.translateLines(
                chips.stream().map(Suggestion::labelKo).toList(), languageCode);

        // 번역 실패한 경우: 못 읽는 칩은 아예 띄우지 않는다.
        if (labels == null) {
            return List.of();
        }

        return IntStream.range(0, chips.size())
                .mapToObj(i -> chips.get(i).withLocalLabel(labels.get(i)))
                .toList();
    }
}
