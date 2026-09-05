package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import com.foten.spending.domain.SpendingCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpendingSuggestionProvider implements SuggestionProvider{
    private static final String SPENDING_TOOL = "getSpendingSummary";
    private static final int MAX_CHIPS = 3;

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        if(!ctx.calledTools().contains(SPENDING_TOOL) || contentKo == null) {
            return List.of();
        }

        List<Suggestion> chips = new ArrayList<>();
        for(String category: SpendingCategory.ALL) {
            if(chips.size() >= MAX_CHIPS) {
                break;
            }

            if(!contentKo.contains(category)) {
                chips.add(Suggestion.ask(category + " 얼마 썼어?"));
            }
        }
        return chips;
    }
}
