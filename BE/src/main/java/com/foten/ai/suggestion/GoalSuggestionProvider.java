package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import org.springframework.stereotype.Component;

import java.util.List;

// 목표 진단 답 뒤의 칩. "얼마나 모을 수 있을까" 에 부족하다고 답한 턴에는 "어디서 줄일 수 있을까" 를 단다.
// 절감 여력을 말한 답의 칩(전체 내역·항목별)은 SpendingSuggestionProvider 가 단다.
@Component
public class GoalSuggestionProvider implements SuggestionProvider {

    static final String DIAGNOSE_TOOL = "diagnoseGoal";
    private static final String CUT_MARK = "줄일 수";
    private static final String SHORT_MARK = "부족";
    private static final Suggestion WHERE_TO_CUT = Suggestion.ask("어디서 소비를 줄일 수 있을까?");

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        if (contentKo == null || !ctx.calledTools().contains(DIAGNOSE_TOOL)) {
            return List.of();
        }
        if (talksAboutCuts(contentKo) || !contentKo.contains(SHORT_MARK)) {
            return List.of();
        }
        return List.of(WHERE_TO_CUT);
    }

    // 답이 절감 여력("~까지 줄일 수 있습니다")을 말했는지. 그 뒤는 내역을 보는 단계다.
    static boolean talksAboutCuts(String contentKo) {
        return contentKo != null && contentKo.contains(CUT_MARK);
    }
}
