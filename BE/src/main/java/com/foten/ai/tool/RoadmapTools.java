package com.foten.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foten.ai.dto.ChatCard;
import com.foten.common.ResourceNotFoundException;
import com.foten.common.RoadmapStateConflictException;
import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.RateConditionAnswer;
import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.service.RoadmapCommandService;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoadmapTools implements ToolProvider{

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 화면이 되감을 때 그릴 카드의 종류. 값은 앱이 관리한다(chat_message.card_type).
    private static final String CARD_RECOMMENDATION = "RECOMMENDATION";
    // 우대조건을 받을 수 있는 유일한 시점. 그 외에는 제출이 NOT_APPLICABLE 로 막힌다.
    private static final String FLOW_ONBOARDING = "ONBOARDING";

    private final RoadmapQueryService roadmapQueryService;
    private final RoadmapCommandService roadmapCommandService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(
                ToolSpec.noArgs(
                        "getRoadmapStatus",
                        """
                        회원의 저축 로드맵이 지금 어떤 상태인지 알려줍니다.
                        로드맵이 있는지, 이번 달에 무엇을 안내해야 하는지, 지난달에 얼마를 모았는지,
                        밀린 금액이 얼마인지, 이번 달에 얼마를 모아야 하는지를 알려줍니다.
                        로드맵·적금·운용 구간·우대조건에 대한 질문이면 이 도구를 먼저 부릅니다.
                        목표를 이룰 수 있는지 묻는 질문은 diagnoseGoal 을 씁니다.
                        이 도구는 읽기만 하므로 사용자에게 묻지 말고 바로 부릅니다.
                        """,
                        (arguments, context) -> describeStatus(roadmapQueryService.getStatus(context.memberId()))),

                ToolSpec.noArgs(
                        "getPreferentialConditionQuestions",
                        """
                        우대금리를 받으려면 확인해야 하는 질문 목록을 가져옵니다.
                        항목마다 코드와 질문 문구가 함께 옵니다.
                        사용자에게 조건을 물어볼 때, 그리고 사용자의 대답을 코드로 옮길 때 씁니다.
                        이 도구는 읽기만 하므로 사용자에게 묻지 말고 바로 부릅니다.
                        """,
                        (arguments, context) -> describeConditions(context.memberId())),

                ToolSpec.noArgs(
                        "startRoadmap",
                        """
                        회원의 저축 로드맵을 새로 만듭니다. 저장이 일어나고 되돌릴 수 없습니다.
                        사용자가 시작하겠다고 말한 뒤에만 부릅니다. 짐작해서 부르지 않습니다.
                        로드맵이 없을 때만 쓸 수 있으므로 getRoadmapStatus 로 먼저 확인합니다.
                        상품 가입은 하지 않습니다 — 우대조건을 받은 뒤에 정해집니다.
                        """,
                        (arguments, context) -> startRoadmap(context.memberId())),

                ToolSpec.stringList(
                        "submitPreferentialConditions",
                        """
                        사용자가 앞으로 지키겠다고 답한 우대금리 조건을 제출하고, 그 조건을 반영한
                        적금 상품 구성을 받아옵니다. 상품 가입이 확정되고 되돌릴 수 없습니다.
                        사용자가 조건을 고른 뒤 제출하겠다고 답한 다음에만 부릅니다.
                        조건에 답한 것만으로는 부족합니다 - 고른 항목을 되읽어 주고
                        "이대로 제출할까요?" 에 그렇다고 답한 뒤에 부릅니다.
                        먼저 getPreferentialConditionQuestions 로 질문과 코드를 확인하세요.
                        해당한다고 답한 조건의 코드만 넘기면 됩니다. 나머지는 서버가 아니오로 처리합니다.
                        아무것도 해당하지 않으면 빈 목록을 넘기세요.
                        """,
                        "conditionCodes",
                        "사용자가 앞으로 지키겠다고 답한 조건의 코드 목록 (예: SALARY_TRANSFER)",
                        (arguments, context) -> submitConditions(
                                context, ToolArguments.stringList(arguments, "conditionCodes"))),

                ToolSpec.noArgs(
                        "getSegmentComposition",
                        """
                        지금 구간에 어떤 적금 상품으로 얼마씩 나눠 모으는지 알려줍니다.
                        상품 이름, 가입 기간, 적용 금리, 상품별 월 납입액이 나옵니다.
                        우대조건을 제출해 상품이 정해진 뒤에만 쓸 수 있습니다.
                        아직이면 getRoadmapStatus 로 어느 단계인지 먼저 확인하세요.
                        """,
                        (arguments, context) -> describeComposition(context)));
    }

    private String describeStatus(RoadmapStatus s) {
        StringBuilder sb = new StringBuilder("[저축 로드맵 상태]\n");

        if (!s.roadmapExists()) {
            sb.append("로드맵: 아직 없음\n");
            sb.append("로드맵이 무엇인지 짧게 설명하고 시작할지 물으세요.");
            sb.append(" 사용자가 하겠다고 답한 다음에 startRoadmap 을 부릅니다.\n");
            sb.append("사용자가 이미 만들겠다고 말했다면 다시 묻지 말고 그 자리에서 startRoadmap 을 부르세요.");
            sb.append(" 한 번 더 확인하는 것은 두 번 묻는 것입니다.\n");
            return sb.toString();
        }

        sb.append("로드맵: 있음\n");
        sb.append("이번 달에 할 안내: ").append(flowGuide(s.flowType())).append("\n");
        appendSegment(sb, s);
        appendLastMonth(sb, s);
        appendAmounts(sb, s);
        sb.append("위 금액들의 차액을 직접 빼서 구하지 마세요. 필요한 값은 이미 위에 있습니다.");
        return sb.toString();
    }

    private String flowGuide(String flowType) {
        if (flowType == null) {
            return "알 수 없습니다. 무엇을 도와드릴지 물어보세요.";
        }
        return switch (flowType) {
            case "ONBOARDING" -> "로드맵을 막 만든 달입니다. 다음은 우대조건 확인입니다"
                    + " (getPreferentialConditionQuestions).";
            case "NEW_SEGMENT" -> "운용 구간이 바뀌는 달입니다. 지난 구간에서 모인 목돈부터 알려주세요.";
            case "REGULAR_MONTH" -> "평소 달입니다. 지난달 결과와 이번 달 저축액을 알려주세요.";
            default -> "알 수 없습니다. 무엇을 도와드릴지 물어보세요.";
        };
    }

    private void appendSegment(StringBuilder sb, RoadmapStatus s) {
        if (s.currentSegmentNo() == null) {
            return;
        }
        sb.append("지금 운용 구간: ").append(s.currentSegmentNo()).append("번째");
        if (Boolean.TRUE.equals(s.isLastSegment())) {
            sb.append(" (마지막 구간)");
        }
        sb.append("\n");

        if (Boolean.TRUE.equals(s.pendingSegmentTransition())) {
            sb.append("이번 달에 이 구간이 끝나고 새 구간이 시작됩니다.\n");
        }
        if (isPositive(s.rolloverAmount())) {
            sb.append("지난 구간에서 모인 목돈: ").append(money(s.rolloverAmount())).append("원\n");
        }
    }

    private void appendLastMonth(StringBuilder sb, RoadmapStatus s) {
        if (s.lastMonthActualAmount() == null) {
            sb.append("지난달 실적: 아직 없음 (이번이 첫 달)\n");
            return;
        }
        sb.append("지난달에 실제로 모은 금액: ").append(money(s.lastMonthActualAmount())).append("원\n");
    }

    private void appendAmounts(StringBuilder sb, RoadmapStatus s) {
        sb.append("매달 모으기로 한 금액: ").append(money(s.baselineAmount())).append("원\n");
        sb.append("이번 달에 모아야 하는 금액: ").append(money(s.requiredAmount())).append("원\n");

        if (Boolean.TRUE.equals(s.hasShortfall()) && isPositive(s.shortfallAmount())) {
            sb.append("지금까지 밀린 금액: ").append(money(s.shortfallAmount())).append("원\n");
        }
        else {
            sb.append("밀린 금액: 없음\n");
        }
    }

    private String startRoadmap(long memberId) {
        try {
            return describeCreated(roadmapCommandService.createRoadmap(memberId));
        }
        catch (RoadmapStateConflictException e) {
            return switch (e.getErrorCode()) {
                case "ROADMAP_ALREADY_EXISTS" -> "이미 로드맵이 있습니다. 새로 만들지 말고"
                        + " getRoadmapStatus 로 지금 상태를 확인해서 안내하세요.";
                case "GOAL_NOT_READY" -> "목표와 체류 정보가 아직 없어 로드맵을 만들 수 없습니다."
                        + " 목표를 먼저 정해야 한다고 안내하세요.";
                default -> "지금은 로드맵을 만들 수 없습니다.";
            };
        }
    }

    private String describeCreated(CreatedRoadmap r) {
        CreatedRoadmap.SegmentSummary segment = r.segment();

        StringBuilder sb = new StringBuilder("[로드맵을 만들었습니다]\n");
        sb.append("매달 모을 금액: ").append(money(r.baselineAmount())).append("원\n");
        sb.append("전체 기간: ").append(r.totalMonths()).append("개월 (오늘부터 귀국 한 달 전까지)\n");
        sb.append("첫 운용 구간: ").append(segment.plannedMonths()).append("개월");
        if (segment.isLastSegment()) {
            sb.append(" — 이 구간이 마지막입니다");
        }
        sb.append("\n");

        // 만든 직후에는 목표기준액과 필요저축액이 같다. 같으면 한 번만 말해야 사용자가 되묻지 않는다.
        if (differs(r.requiredAmount(), r.baselineAmount())) {
            sb.append("이번 달에만 모아야 하는 금액: ").append(money(r.requiredAmount())).append("원\n");
        }

        sb.append("말하는 방법:\n");
        sb.append("- 세 문장 안팎으로 짧게 말하세요. 값을 한 줄에 하나씩 늘어놓지 마세요.\n");
        sb.append("- 매달 모을 금액을 먼저, 가장 중요하게 다루세요. 기간과 구간은 곁들이는 정도입니다.\n");
        sb.append("- 날짜를 그대로 읽지 말고 '오늘부터 몇 개월' 처럼 기간으로 말하세요.\n");
        sb.append("- 첫 달은 밀린 금액이 없어 따로 정할 것이 없다고 알려주세요.\n");
        sb.append("- 마지막에 '이 금액을 어떤 상품에 나눠 모을지 정해볼게요' 처럼 다음 단계를 알리세요.\n");
        sb.append("- 상품은 아직 정해지지 않았습니다. 상품 이름을 지어내지 마세요.\n");
        return sb.toString();
    }

    // BigDecimal 은 소수점 자릿수가 다르면 equals 가 false 다. 금액 비교는 compareTo 로 한다.
    private boolean differs(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) != 0;
    }

    // 모델이 코드를 빠뜨리거나 지어낼 수 있어, 실제 조건 목록에 맞춰 예/아니오를 채운다.
    private String submitConditions(ToolContext context, List<String> agreedCodes) {
        long memberId = context.memberId();
        List<RateConditionAnswer> answers = roadmapQueryService.getRateConditions().stream()
                .map(condition -> new RateConditionAnswer(
                        condition.getConditionCode(),
                        agreedCodes.contains(condition.getConditionCode())))
                .toList();

        try {
            return describeComposition(context,
                    roadmapCommandService.submitRateConditionResponses(memberId, answers));
        }
        catch (RoadmapStateConflictException e) {
            return switch (e.getErrorCode()) {
                case "NOT_APPLICABLE" -> notApplicableReason(memberId);
                case "GOAL_NOT_READY" -> "목표가 아직 확정되지 않아 상품을 정할 수 없습니다.";
                default -> "지금은 우대조건을 제출할 수 없습니다.";
            };
        }
    }

    // NOT_APPLICABLE 은 로드맵이 없을 때도 온다. 구분하지 않으면 없는 상품을 있다고 말한다.
    private String notApplicableReason(long memberId) {
        if (!roadmapQueryService.getStatus(memberId).roadmapExists()) {
            return "아직 로드맵이 없어 우대조건을 받을 수 없습니다."
                    + " 로드맵을 먼저 만들어야 한다고 안내하세요.";
        }
        return "이미 우대조건을 제출해 상품이 정해져 있습니다."
                + " 다시 제출할 수 없다고 알리고, 지금 상품 구성을 보고 싶은지 물어보세요.";
    }

    // 가입 전에는 IllegalStateException 이 난다. 던지면 ToolRegistry 가 이유를 뭉갠다.
    private String describeComposition(ToolContext context) {
        try {
            return describeComposition(context,
                    roadmapQueryService.getCurrentComposition(context.memberId()));
        }
        catch (IllegalStateException | ResourceNotFoundException e) {
            return "아직 상품이 정해지지 않았습니다. 우대조건을 먼저 확인해야 한다고 안내하세요.";
        }
    }

    // 제출(4-4)과 조회(4-5)가 같은 타입을 돌려주므로 문장 만드는 코드를 함께 쓴다.
    private String describeComposition(ToolContext context, SegmentComposition c) {
        addCard(context, c);

        StringBuilder sb = new StringBuilder("[이번 구간 상품 구성]\n");
        sb.append("매달 모을 기준 금액: ").append(money(c.monthlyBaseline())).append("원\n");

        for (SegmentComposition.SavingsSummary s : c.savings()) {
            sb.append("적금 | ").append(s.productName())
                    .append(" | ").append(s.termMonths()).append("개월")
                    .append(" | 금리 ").append(s.appliedRate()).append("%")
                    .append(" | 월 ").append(money(s.monthlyAllocated())).append("원\n");
        }

        if (c.deposit() != null) {
            SegmentComposition.DepositSummary d = c.deposit();
            sb.append("예금 | ").append(d.productName())
                    .append(" | ").append(d.termMonths()).append("개월")
                    .append(" | 금리 ").append(d.appliedRate()).append("%")
                    .append(" | 목돈 ").append(money(d.principal())).append("원\n");
        }

        if (isPositive(c.recommendedCashSaving())) {
            sb.append("적금에 담지 못하고 현금으로 두는 금액: ")
                    .append(money(c.recommendedCashSaving())).append("원\n");
        }

        sb.append("말하는 방법:\n");
        sb.append("- 상품마다 한 줄로, 이름과 월 납입액을 먼저 말하세요. 금리와 기간은 뒤에 붙입니다.\n");
        sb.append("- 금액과 금리는 위 값 그대로 옮기고 직접 더하거나 빼지 마세요.\n");
        sb.append("- 예금이 없으면 목돈이 아직 없어서라고 알려주세요.\n");
        sb.append("- 이자와 만기 금액은 아직 계산되지 않았습니다. 지어내지 마세요.\n");
        return sb.toString();
    }

    // 이미 제출한 뒤에 질문을 다시 늘어놓으면 답해도 제출이 막혀 막다른 길이 된다.
    private String describeConditions(long memberId) {
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        if (!status.roadmapExists()) {
            return "아직 로드맵이 없어 우대조건을 받을 수 없습니다.\n"
                    + "로드맵을 먼저 만들어야 한다고 안내하세요. 조건 목록을 늘어놓지 마세요.\n";
        }
        if (!FLOW_ONBOARDING.equals(status.flowType())) {
            return "우대조건은 이미 제출했고 상품이 정해져 있습니다.\n"
                    + "이미 정해져서 다시 받을 수 없다고 알리고, 지금 상품 구성을 보여줄지 물어보세요.\n"
                    + "조건 목록을 늘어놓거나 번호를 묻지 마세요. 고를 수 있는 것이 없습니다.\n";
        }

        List<RateConditionVO> conditions = roadmapQueryService.getRateConditions();
        if (conditions == null || conditions.isEmpty()) {
            return "우대금리 조건 질문이 없습니다.";
        }

        StringBuilder sb = new StringBuilder("[우대금리 조건 질문]\n");
        int no = 1;
        for (RateConditionVO condition : conditions) {
            sb.append(no++).append(". 코드 ").append(condition.getConditionCode())
                    .append(" | ").append(condition.getLabel());
            if (condition.getDescription() != null && !condition.getDescription().isBlank()) {
                sb.append(" | ").append(condition.getDescription());
            }
            sb.append("\n");
        }

        // 같은 항목이 체크박스로도 나간다. 질문을 다 늘어놓으면 화면에 두 번 보인다.
        sb.append("보여주는 방법:\n");
        sb.append("- 이 항목들은 체크박스로도 함께 나갑니다. 항목 이름을 하나도 적지 마세요.\n");
        sb.append("- 번호도 기호도 붙이지 말고 목록 자체를 만들지 마세요. 목록은 체크박스가 보여줍니다.\n");
        sb.append("- '받을 수 있는 우대금리를 확인할게요. 앞으로 지킬 수 있는 것을 모두 골라주세요'\n");
        sb.append("  이 두 문장만 말하고 끝내세요.\n");
        sb.append("- 코드는 내부용입니다. 사용자에게 보여주지 마세요.\n");
        sb.append("- 사용자가 고른 뒤 제출하겠다고 답하기 전에는\n");
        sb.append("  submitPreferentialConditions 를 부르지 마세요.\n");
        sb.append("사용자의 대답이 어느 코드에 해당하는지는 기억해 두세요.\n");
        return sb.toString();
    }

    // 되감을 때 다시 계산하면 옛 대화에 새 구성이 붙는다. 그때 값을 그대로 남긴다.
    private void addCard(ToolContext context, SegmentComposition composition) {
        try {
            context.cards().add(new ChatCard(CARD_RECOMMENDATION, MAPPER.writeValueAsString(composition)));
        }
        catch (Exception e) {
            // 카드를 못 만들어도 답변은 나가야 한다. 예외 메시지에 금액이 실려 종류만 남긴다.
            log.warn("카드를 만들지 못했습니다. type={} ({})", CARD_RECOMMENDATION, e.getClass().getSimpleName());
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }

}
