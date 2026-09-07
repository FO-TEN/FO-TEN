package com.foten.ai.service;

import com.foten.ai.advisor.Advisor;
import com.foten.ai.advisor.AdvisorChain;
import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.ChatCard;
import com.foten.ai.dto.ChatReply;
import com.foten.ai.dto.Suggestion;
import com.foten.ai.llm.LlmChatResponse;
import com.foten.ai.llm.LlmClient;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.llm.LlmToolCall;
import com.foten.member.mapper.MemberLanguageMapper;
import com.foten.ai.prompt.SystemPrompt;
import com.foten.ai.suggestion.SuggestionProvider;
import com.foten.ai.tool.ToolContext;
import com.foten.ai.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    // 마지막 왕복은 답을 쓰는 데 쓰인다. 3이면 도구를 두 번밖에 못 부르는데,
    // 이번 달을 확정하는 흐름은 상태 조회·확정·배분 확인으로 세 번이 필요하다.
    private static final int MAX_TOOL_ROUNDS = 4;
    // 다시 쓰게 할 때 붙이는 지시. 도구 결과는 이미 대화에 있어 도구 없이도 같은 답을 만들 수 있다.
    private static final String REWRITE_KOREAN =
            "직전 답에 한국어가 아닌 문자 체계의 글자가 섞였습니다. "
            + "같은 내용을 한국어와 숫자·기호만 써서 다시 답하세요. 내용을 더하거나 빼지 마세요.";

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

        String contentKo = koreanOnly(ctx, AdvisorChain.of(advisors, this::runToolLoop).next(ctx));
        String contentLocal = translator.translate(contentKo, languageCode);

        // 한 턴에 카드가 여럿 담기면 마지막 것을 쓴다. 답변이 다루는 것은 마지막으로 부른 도구다.
        ChatCard card = ctx.cards().isEmpty() ? null : ctx.cards().get(ctx.cards().size() - 1);

        chatMemory.addUserMessage(memberId, null, message, languageCode);
        chatMemory.addAssistantMessage(memberId, contentKo, contentLocal, languageCode, card);

        return new ChatReply(contentKo, contentLocal, card, suggestions(ctx, contentKo, languageCode));
    }

    private String runToolLoop(ChatContext ctx) {
        ToolContext toolContext = new ToolContext(ctx.memberId(), ctx.languageCode(), ctx.cards());

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

    // 모델이 한국어 답 사이에 다른 문자 체계의 글자를 드물게 섞는다(예: 한 단어가 구자라트 문자로).
    // 규칙만으로는 100% 막히지 않아 검사한다. 한 번 다시 쓰게 하고, 그래도 남으면 그 글자만 걷어낸다.
    // 걷어내면 단어 하나가 빌 수 있지만 낯선 문자가 화면에 찍히는 것보다 낫다. 본문은 로그에 남기지 않는다.
    private String koreanOnly(ChatContext ctx, String draft) {
        if (KoreanScript.isClean(draft)) {
            return draft;
        }
        log.warn("답변에 한국어가 아닌 글자가 섞여 다시 씁니다. count={}", KoreanScript.foreignCount(draft));
        try {
            List<LlmMessage> retry = new ArrayList<>(ctx.messages());
            retry.add(LlmMessage.system(REWRITE_KOREAN));
            String rewritten = llmClient.call(retry);
            if (KoreanScript.isClean(rewritten)) {
                return rewritten;
            }
            log.warn("다시 쓴 답에도 남아 걷어냅니다. count={}", KoreanScript.foreignCount(rewritten));
        }
        catch (RuntimeException e) {
            log.warn("다시 쓰기 실패 ({}) - 걷어냅니다.", e.getClass().getSimpleName());
        }
        return KoreanScript.strip(draft);
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
