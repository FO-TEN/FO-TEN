package com.foten.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foten.ai.dto.ChatCard;
import com.foten.spending.domain.CategoryTotal;
import com.foten.spending.domain.MonthlySpending;
import com.foten.spending.service.SpendingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpendingTools implements ToolProvider {
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 카테고리별 소비를 도넛으로 그리는 카드. 값은 앱이 관리한다(chat_message.card_type).
    private static final String CARD_SPENDING = "SPENDING";

    private final SpendingQueryService spendingQueryService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(
                ToolSpec.noArgs(
                        "getSpendingSummary",
                        """
                        회원의 이번 달과 지난달 소비 금액을 알려줍니다.
                        전체 지출, 고정비와 변동비, 항목별(식비·교통·통신·쇼핑·기타·주거) 금액이 나옵니다.
                        "얼마 썼어?" "제일 많이 쓴 게 뭐야?" "가장 큰 비중을 차지하는 항목은?"
                        "식비 얼마 썼어?" 처럼 한두 가지를 콕 집어 묻는 질문에 씁니다.
                        1위·순위·비중을 묻는 질문도 여기입니다 — 답이 항목 하나로 끝나면 이 도구입니다.
                        목록을 통째로 펼쳐 달라는 질문이면 getSpendingBreakdown 을 씁니다.
                        이미 쓴 금액만 알려줍니다. 앞으로 얼마나 줄일 수 있는지는 diagnoseGoal 을 씁니다.
                        """,
                        (arguments, context) -> describe(context)),

                ToolSpec.noArgs(
                        "getSpendingBreakdown",
                        """
                        이번 달 소비를 항목별 도넛 그래프 카드로 보여줍니다.
                        "전체 내역 볼래" "항목별로 다 보여줘" "소비 내역 보여줘" 처럼
                        목록 전체를 펼쳐 달라고 할 때만 씁니다.
                        항목 하나로 답이 끝나는 질문에는 절대 쓰지 않습니다.
                        "제일 많이 쓴 항목" "가장 큰 비중" "식비 얼마" 는 모두 getSpendingSummary 입니다.
                        "전체" "전부" "다" "내역" 처럼 목록을 달라는 말이 없으면 이 도구가 아닙니다.
                        """,
                        (arguments, context) -> describeBreakdown(context)));
    }

    private String describe(ToolContext context) {
        long memberId = context.memberId();
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
        // 화면(홈·소비내역)이 보여주는 것과 같은 값이다. 고정비·변동비를 합쳐 많이 쓴 순으로 뽑은
        // 것이라, 밑의 두 갈래를 모델이 직접 더해 만들 필요가 없다.
        appendCategoryTotals(sb, spending.categoryTotals());
        sb.append("  고정비: ").append(money(spending.fixedTotal()))
                .append("원 (월세·통신요금처럼 매달 나가는 돈)\n");
        appendCategories(sb, spending.fixedByCategory());
        sb.append("  변동비: ").append(money(spending.variableTotal()))
                .append("원 (줄일 수 있는 소비)\n");
        appendCategories(sb, spending.variableByCategory());
    }

    private void appendCategoryTotals(StringBuilder sb, List<CategoryTotal> categoryTotals) {
        if (categoryTotals == null || categoryTotals.isEmpty()) {
            return;
        }
        sb.append("  많이 쓴 순 (고정비+변동비 합산):\n");
        int rank = 1;
        for (CategoryTotal c : categoryTotals) {
            sb.append("    ").append(rank++).append(". ").append(c.category())
                    .append(": ").append(money(c.amount())).append("원\n");
        }
    }

    private void appendCategories(StringBuilder sb, Map<String, BigDecimal> byCategory) {
        byCategory.forEach((category, amount) ->
                sb.append("    ").append(category).append(": ").append(money(amount)).append("원\n"));
    }

    /*
     * 전체를 훑어보려는 질문 전용. 카드가 항목·금액·비중을 다 그리므로 글은 한 문장만 쓰게 한다.
     * 같은 값을 글로도 늘어놓으면 화면에 같은 숫자가 두 번 보인다.
     */
    private String describeBreakdown(ToolContext context) {
        MonthlySpending current = spendingQueryService.getMonthlySpending(context.memberId(), 0);

        if (current.total().signum() == 0) {
            return "이번 달은 아직 쓴 내역이 없습니다. 항목별로 보여줄 것이 없다고 알려주세요.";
        }
        addSpendingCard(context, current);

        StringBuilder sb = new StringBuilder("[이번 달 항목별 소비]\n");
        sb.append("기간: ").append(current.month().getMonthValue()).append("월 1일~")
                .append(current.daysCovered()).append("일 (아직 진행 중)\n");
        sb.append("전체: ").append(money(current.total())).append("원\n");
        appendCategoryTotals(sb, current.categoryTotals());

        sb.append("\n보여주는 방법:\n");
        sb.append("- 항목별 도넛 그래프 카드가 답변과 함께 나갑니다. 카드가 항목·금액·비중을 다 보여줍니다.\n");
        sb.append("- 그래서 글로는 항목을 하나도 읊지 마세요. 금액도 적지 마세요.\n");
        // 날짜를 말하게 하면 번역을 거치며 "9월 1일~8일" 이 "8월 1일~9월" 처럼 뒤집힌다.
        // 며칠까지인지는 이 답에 꼭 필요한 값이 아니라 아예 말하지 않게 한다.
        sb.append("- 날짜 범위는 말하지 마세요. \"이번 달\" 이라고만 합니다.\n");
        sb.append("- 전체 금액만 한 문장으로 말하고 아래 카드를 보라고 알려주세요.\n");
        sb.append("- 예: \"이번 달 834,650원 쓰셨어요. 항목별로는 아래에서 확인해 보세요.\"\n");
        sb.append("- 소비가 늘거나 줄어든 이유는 이 결과에 없습니다. 지어내지 마세요.\n");
        return sb.toString();
    }

    private void appendRules(StringBuilder sb, int daysPassed) {
        sb.append("\n이번 달은 아직 ").append(daysPassed).append("일까지이고 지난달은 한 달 전체입니다.\n");
        sb.append("두 금액을 직접 비교하지 마세요. 이번 달 예상 금액을 만들어내지도 마세요.\n");
        sb.append("비교해 달라고 하면 이번 달이 끝나야 견줄 수 있다고 답하세요.\n");
        sb.append("항목별에 없는 항목은 0원입니다.\n");
        sb.append("무엇에 많이 썼는지 물으면 '많이 쓴 순'을 그대로 옮기세요. 화면도 같은 값을 보여줍니다.\n");
        sb.append("고정비 항목과 변동비 항목을 직접 더하지 마세요. 합친 값이 필요하면 '많이 쓴 순'에 있습니다.\n");
        // 여기 값이 다 있다고 다 말하면 안 된다. 하나를 물었는데 다섯을 늘어놓으면 답이 아니라 목록이 된다.
        sb.append("물어본 것만 답하세요. 1위를 물으면 1위만, 식비를 물으면 식비만 말합니다.\n");
        sb.append("묻지 않은 항목까지 늘어놓지 마세요. 전체를 보고 싶어 하면 getSpendingBreakdown 을 부르세요.\n");
        sb.append("각 항목을 얼마나 줄일 수 있는지는 이 결과에 없습니다.\n");
        sb.append("소비가 늘거나 줄어든 이유는 이 결과에 없습니다. 금액만 말하고 이유를 지어내지 마세요.");
    }

    /*
     * 소비내역 화면의 도넛과 같은 값을 그대로 보낸다 — categoryTotals 는 이미 금액 내림차순이고
     * SpendingDonut 이 그 순서를 그대로 그린다.
     *
     * 카드가 그리는 값만 담는다. 기간("9월 8일까지")은 카드에서 뺐으므로 보내지 않는다.
     */
    private void addSpendingCard(ToolContext context, MonthlySpending spending) {
        if (spending.categoryTotals() == null || spending.categoryTotals().isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", spending.total());
        payload.put("categoryTotals", spending.categoryTotals());

        try {
            context.cards().add(new ChatCard(CARD_SPENDING, MAPPER.writeValueAsString(payload)));
        }
        catch (Exception e) {
            // 카드를 못 만들어도 답변은 나가야 한다. 예외 메시지에 금액이 실려 종류만 남긴다.
            log.warn("카드를 만들지 못했습니다. type={} ({})", CARD_SPENDING, e.getClass().getSimpleName());
        }
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }
}
