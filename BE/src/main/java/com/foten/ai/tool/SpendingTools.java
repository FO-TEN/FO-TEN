package com.foten.ai.tool;

import com.foten.spending.domain.MonthlySpending;
import com.foten.spending.service.SpendingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SpendingTools implements ToolProvider {
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");
    private final SpendingQueryService spendingQueryService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(ToolSpec.noArgs(
                "getSpendingSummary",
                """
                회원의 이번 달과 지난달 소비 내역을 알려줍니다.
                전체 지출, 고정비와 변동비, 항목별(식비·교통·통신·쇼핑·기타) 금액을 알려줍니다.
                사용자가 얼마 썼는지·무엇에 많이 썼는지·어떤 항목에 얼마 썼는지 물을 때 사용합니다.
                """,
                (arguments, context) -> describe(context.memberId())));
    }

    private String describe(long memberId) {
        MonthlySpending current = spendingQueryService.getMonthlySpending(memberId, 0);
        MonthlySpending previous = spendingQueryService.getMonthlySpending(memberId, 1);

        if(current.total().signum() == 0 && previous.total().signum() == 0) {
            return "소비 내역이 아직 없습니다.";
        }

        StringBuilder sb = new StringBuilder("[소비 내역]\n");
        sb.append("\n이번 달 ").append(current.month().getMonthValue()).append("월 1일~")
                .append(current.daysCovered()).append("일 (아직 진행 중)\n");
        appendBreakdown(sb, current);

        sb.append("\n지난달 ").append(previous.month().getMonthValue()).append("월 한 달 전체\n");
        appendBreakdown(sb, previous);

        appendRules(sb, current.daysCovered());
        return sb.toString();
    }

    private void appendBreakdown(StringBuilder sb, MonthlySpending spending) {
        sb.append("  전체: ").append(money(spending.total())).append("원\n");
        sb.append("  고정비: ").append(money(spending.fixedTotal()))
                .append("원 (월세·통신요금처럼 매달 나가는 돈)\n");
        appendCategories(sb, spending.fixedByCategory());
        sb.append("  변동비: ").append(money(spending.variableTotal()))
                .append("원 (줄일 수 있는 소비)\n");
        appendCategories(sb, spending.variableByCategory());
    }

    private void appendCategories(StringBuilder sb, Map<String, BigDecimal> byCategory) {
        byCategory.forEach((category, amount) ->
                sb.append("    ").append(category).append(": ").append(money(amount)).append("원\n"));
    }

    private void appendRules(StringBuilder sb, int daysPassed) {
        sb.append("\n이번 달은 아직 ").append(daysPassed).append("일까지이고 지난달은 한 달 전체입니다.\n");
        sb.append("두 금액을 직접 비교하지 마세요. 이번 달 예상 금액을 만들어내지도 마세요.\n");
        sb.append("비교해 달라고 하면 이번 달이 끝나야 견줄 수 있다고 답하세요.\n");
        sb.append("항목별에 없는 항목은 0원입니다.\n");
        sb.append("고정비 항목과 변동비 항목을 섞지 마세요.\n");
        sb.append("각 항목을 얼마나 줄일 수 있는지는 이 결과에 없습니다.\n");
        sb.append("소비가 늘거나 줄어든 이유는 이 결과에 없습니다. 금액만 말하고 이유를 지어내지 마세요.");
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }
}
