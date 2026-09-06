package com.foten.ai.tool;

import com.foten.common.RoadmapStateConflictException;
import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.service.RoadmapCommandService;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RoadmapTools implements ToolProvider{

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

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
                        (arguments, context) -> describeConditions(roadmapQueryService.getRateConditions())),

                ToolSpec.noArgs(
                        "startRoadmap",
                        """
                        회원의 저축 로드맵을 새로 만듭니다. 저장이 일어나고 되돌릴 수 없습니다.
                        사용자가 시작하겠다고 말한 뒤에만 부릅니다. 짐작해서 부르지 않습니다.
                        로드맵이 없을 때만 쓸 수 있으므로 getRoadmapStatus 로 먼저 확인합니다.
                        상품 가입은 하지 않습니다 — 우대조건을 받은 뒤에 정해집니다.
                        """,
                        (arguments, context) -> startRoadmap(context.memberId())));
    }

    private String describeStatus(RoadmapStatus s) {
        StringBuilder sb = new StringBuilder("[저축 로드맵 상태]\n");

        if (!s.roadmapExists()) {
            sb.append("로드맵: 아직 없음\n");
            sb.append("로드맵이 무엇인지 짧게 설명하고 시작할지 물으세요.");
            sb.append(" 사용자가 하겠다고 답한 다음에 startRoadmap 을 부릅니다.\n");
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
        sb.append("전체 기간: ").append(r.totalMonths()).append("개월\n");
        sb.append("매달 모으기로 한 금액: ").append(money(r.baselineAmount())).append("원\n");
        sb.append("이번 달에 모아야 하는 금액: ").append(money(r.requiredAmount())).append("원\n");
        sb.append("첫 운용 구간: ").append(segment.segmentNo()).append("번째, ")
                .append(segment.plannedMonths()).append("개월 (")
                .append(segment.startDate()).append(" ~ ").append(segment.endDate()).append(")");
        if (segment.isLastSegment()) {
            sb.append(" — 마지막 구간");
        }
        sb.append("\n");
        sb.append("상품 가입은 아직 하지 않았습니다. 다음은 우대조건 확인이라고 안내하세요.\n");
        return sb.toString();
    }

    private String describeConditions(List<RateConditionVO> conditions) {
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

        sb.append("보여주는 방법:\n");
        sb.append("- 먼저 '받을 수 있는 우대금리를 확인할게요' 처럼 한 줄로 안내하세요.\n");
        sb.append("- 질문마다 앞에 번호를 붙이고, 질문과 질문 사이에 빈 줄을 넣으세요.\n");
        sb.append("- 마지막에 '해당하는 번호를 모두 알려주세요' 라고 덧붙이세요.\n");
        sb.append("- 코드는 내부용입니다. 사용자에게 보여주지 말고 질문 문구만 말하세요.\n");
        sb.append("사용자의 대답이 어느 코드에 해당하는지는 기억해 두세요.\n");
        return sb.toString();
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }

}
