package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpendingSuggestionProvider implements SuggestionProvider{
    private static final String SPENDING_TOOL = "getSpendingSummary";
    private static final String BREAKDOWN_TOOL = "getSpendingBreakdown";
    private static final Suggestion BREAKDOWN = Suggestion.ask("전체 소비 내역 보기");

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        // 전체 내역을 이미 보여준 뒤에는 칩을 달지 않는다. 카드에 다 나와 있어 더 물을 것이 없다.
        if(ctx.calledTools().contains(BREAKDOWN_TOOL)) {
            return List.of();
        }
        if(contentKo == null) {
            return List.of();
        }
        // 소비 조회 답, 그리고 절감 여력을 말한 진단 답 뒤에는 전체 내역으로 이어준다.
        boolean spendingAnswer = ctx.calledTools().contains(SPENDING_TOOL);
        boolean cutAnswer = ctx.calledTools().contains(GoalSuggestionProvider.DIAGNOSE_TOOL)
                && GoalSuggestionProvider.talksAboutCuts(contentKo);
        if(!spendingAnswer && !cutAnswer) {
            return List.of();
        }
        return List.of(BREAKDOWN);
    }
}
