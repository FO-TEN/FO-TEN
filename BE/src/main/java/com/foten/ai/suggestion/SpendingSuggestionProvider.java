package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.dto.Suggestion;
import com.foten.spending.domain.CategoryTotal;
import com.foten.spending.domain.SpendingCategory;
import com.foten.spending.service.SpendingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpendingSuggestionProvider implements SuggestionProvider{
    private static final String SPENDING_TOOL = "getSpendingSummary";
    private static final String BREAKDOWN_TOOL = "getSpendingBreakdown";
    // 전체 내역 칩이 한 자리를 먼저 가져가므로 항목 칩은 둘까지만 붙는다.
    private static final int MAX_CATEGORY_CHIPS = 2;
    private static final Suggestion BREAKDOWN = Suggestion.ask("전체 내역 볼래");

    private final SpendingQueryService spendingQueryService;

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        // 전체 내역을 이미 보여준 뒤에는 칩을 달지 않는다. 카드에 다 나와 있어 더 물을 것이 없다.
        if(ctx.calledTools().contains(BREAKDOWN_TOOL)) {
            return List.of();
        }
        if(!ctx.calledTools().contains(SPENDING_TOOL) || contentKo == null) {
            return List.of();
        }

        // 답이 하나만 말하고 끝나므로, 전체를 보고 싶을 때 갈 곳을 첫 칩으로 준다.
        List<Suggestion> chips = new ArrayList<>();
        chips.add(BREAKDOWN);

        for(String category: nextCategories(ctx.memberId(), contentKo)) {
            chips.add(Suggestion.ask(category + " 얼마 썼어?"));
        }
        return chips;
    }

    /*
     * 답이 1위 하나를 말하고 끝나므로 그다음으로 큰 항목을 물어볼 자리를 준다.
     * categoryTotals 는 금액 내림차순이라 큰 것부터 집힌다 — 고정 순서로 주면 이번 달에 거의
     * 쓰지 않은 항목을 먼저 권하게 되고, 눌러도 "0원입니다" 만 나온다.
     * 답변이 이미 말한 항목은 뺀다. 방금 들은 말을 다시 물어보는 칩은 자리만 차지한다.
     */
    private List<String> nextCategories(long memberId, String contentKo) {
        List<String> ordered = orderedByAmount(memberId);

        List<String> picked = new ArrayList<>();
        for(String category: ordered) {
            if(picked.size() >= MAX_CATEGORY_CHIPS) {
                break;
            }

            if(!contentKo.contains(category)) {
                picked.add(category);
            }
        }
        return picked;
    }

    private List<String> orderedByAmount(long memberId) {
        try {
            List<CategoryTotal> totals = spendingQueryService.getMonthlySpending(memberId, 0).categoryTotals();
            if(totals != null && !totals.isEmpty()) {
                return totals.stream().map(CategoryTotal::category).toList();
            }
        }
        catch(Exception e) {
            // 칩을 못 만든다고 답변까지 막지는 않는다. 예외에 금액이 실려 종류만 남긴다.
            log.warn("소비 칩 순서를 읽지 못했습니다. ({})", e.getClass().getSimpleName());
        }
        return SpendingCategory.ALL;
    }
}
