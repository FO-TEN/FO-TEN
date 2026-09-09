package com.foten.ai.tool;

import com.foten.goal.domain.CategorySavingPotential;
import com.foten.goal.dto.GoalDiagnosisResponse;
import com.foten.goal.service.GoalDiagnosisService;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GoalTools implements ToolProvider{

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");
    private final GoalDiagnosisService goalDiagnosisService;
    private final RoadmapQueryService roadmapQueryService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(ToolSpec.noArgs(
                "diagnoseGoal",
                """
                회원의 목표 저축 달성 가능성을 진단합니다.
                목표 달성 여부, 매달 모으기로 한 금액과 이번 달에 모아야 하는 금액,
                지금까지 밀린 금액,
                목표를 세운 지 몇 개월째인지, 그동안 모았어야 할 금액과 실제로 모은 금액,
                소비 항목마다 한 달에 얼마까지 줄일 수 있는지를 알려줍니다.
                사용자가 목표 달성 여부·저축 현황·절약 방법을 물을 때 사용합니다.
                "식비는 얼마나 줄일 수 있어?" 처럼 절감 여력을 묻는 질문도 이 도구로 답합니다.
                "이번 달에 얼마나 모을 수 있을까?" 처럼 지금 소비로 얼마를 모을 수 있는지 묻는
                질문도 이 도구입니다. 로드맵 상태 도구가 아닙니다.
                이미 쓴 금액을 묻는 질문은 getSpendingSummary 를 씁니다.
                """,
                (arguments, context) -> describe(context.memberId(), goalDiagnosisService.diagnose(context.memberId()))));
    }

    private String describe(long memberId, GoalDiagnosisResponse r) {
        StringBuilder sb = new StringBuilder();

        sb.append("[목표 진단]\n");
        sb.append("판정: ").append(r.judgeResult())
                .append(" - 지금 소비 속도를 기준으로 한 진단입니다. 사용자가 세운 계획은 없으므로")
                .append(" '계획대로' 라는 표현을 쓰지 말고 '지금처럼 쓰면' 이라고 말하세요.\n");
        appendTargets(sb, memberId, r);
        sb.append("지금 소비 속도로 예상되는 저축액: ")
                .append(money(r.currentExpectedSaving())).append("원").append("\n");

        appendGap(sb, r);
        appendSavingTip(sb, r);
        appendShortfall(sb, r);

        sb.append("달성률: ").append(r.achievementRate()).append("%\n");
        sb.append("위 금액들의 차액을 직접 빼서 구하지 마세요. 필요한 차액은 이미 위에 있습니다.\n");
        appendAnswerRules(sb);
        return sb.toString();
    }

    // 질문마다 답할 범위가 다르다. 값이 다 있다고 다 말하면 "얼마나 모을 수 있어" 에 절감 항목까지
    // 늘어놓고, "어디서 줄일 수 있어" 에 예상 저축액을 되풀이한다.
    private void appendAnswerRules(StringBuilder sb) {
        sb.append("\n답하는 방법:\n");
        sb.append("- 얼마나 모을 수 있는지 물으면 세 가지만 말합니다: 지금처럼 쓰면 예상되는 저축액, ")
                .append("이번 달에 모아야 하는 금액(없으면 매달 모으기로 한 금액), 부족한 금액(또는 여유 금액).\n");
        sb.append("  예: \"지금처럼 쓰면 이번 달 예상 저축액은 889,729원입니다. ")
                .append("이번 달에 모아야 하는 금액은 1,098,169원이라 202,558원이 부족합니다.\"\n");
        sb.append("  어디서 줄일 수 있는지는 말하지 마세요. 다음 질문에서 다룹니다.\n");
        sb.append("- 어디서 줄일 수 있는지 물으면 절감 여력만 말합니다. 가장 많이 줄일 수 있는 항목과 금액을 먼저, ")
                .append("나머지 항목을 이어서 말한 뒤, 줄을 바꿔 그 항목만 줄였을 때와 모든 항목을 줄였을 때 예상 저축액을 덧붙입니다.\n");
        sb.append("  예 (첫 문단): \"가장 많이 줄일 수 있는 항목은 쇼핑입니다. 한 달에 242,674원까지 줄일 수 있습니다. ")
                .append("식비는 한 달에 14,610원, 교통은 한 달에 3,019원까지 줄일 수 있습니다.\"\n");
        sb.append("  예 (둘째 문단): \"쇼핑만 줄였을 때 예상 저축액은 1,132,403원입니다. ")
                .append("모든 항목을 줄였을 때 예상 저축액은 1,150,032원입니다.\"\n");
        sb.append("  지금 예상되는 저축액과 부족한 금액은 다시 말하지 마세요.\n");
        sb.append("- 목표를 이룰 수 있는지 물으면 판정과 그 근거가 되는 금액을 말합니다.");
    }

    // 매달 모으기로 한 금액은 고정이고, 이번 달에 모아야 하는 금액은 밀린 만큼 커진다.
    // 로드맵 도구와 같은 이름을 쓴다. 이름이 다르면 같은 달을 두 금액으로 답하게 된다.
    private void appendTargets(StringBuilder sb, long memberId, GoalDiagnosisResponse r) {
        sb.append("매달 모으기로 한 금액: ").append(money(r.monthlyBaseline())).append("원\n");

        // 로드맵이 없는 회원은 이 값이 0으로 온다. 그대로 말하면 안 모아도 된다는 뜻이 된다.
        BigDecimal required = monthlyRequired(memberId, r);
        if (!isPositive(required) || required.compareTo(r.monthlyBaseline()) == 0) {
            return;
        }
        sb.append("이번 달에 모아야 하는 금액: ").append(money(required)).append("원\n");
        sb.append("이번 달 이야기는 이 금액으로 하세요. 밀린 만큼 커진 금액입니다.\n");
    }

    // 로드맵이 있으면 로드맵 상태의 필요저축액을 쓴다. goal 컬럼은 밀린 금액을 전액 더한 값이라,
    // 나눠 갚기로 정한 회원에게는 로드맵 카드와 다른 숫자가 나간다.
    private BigDecimal monthlyRequired(long memberId, GoalDiagnosisResponse r) {
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        if (status.roadmapExists() && status.requiredAmount() != null) {
            return status.requiredAmount();
        }
        return r.monthlyRequired();
    }

    private void appendGap(StringBuilder sb, GoalDiagnosisResponse r) {
        BigDecimal gap = r.additionalNeeded();
        // 아래 금액은 매달 모으기로 한 금액에서 뺀 값이다. 기준을 안 밝히면
        // 이번 달에 모아야 하는 금액에서 뺀 것으로 읽힌다.
        if (gap == null || gap.signum() == 0) {
            sb.append("매달 모으기로 한 금액까지 부족한 금액: 없음\n");
        } else if (gap.signum() > 0) {
            sb.append("매달 모으기로 한 금액까지 부족한 금액: ")
                    .append(money(gap)).append("원").append("\n");
        } else {
            sb.append("매달 모으기로 한 금액을 넘어선 여유 금액: ")
                    .append(money(gap.negate())).append("원").append("\n");
        }
    }

    // 이번 달이 아니라 목표를 세운 뒤 누적이다. "밀린 금액" 만으로는 이번 달 얘기로 읽힌다.
    private void appendShortfall(StringBuilder sb, GoalDiagnosisResponse r) {
        sb.append("목표를 세운 지 ").append(r.elapsedMonths()).append("개월째\n");
        sb.append("그동안 모았어야 할 금액 (누적): ").append(money(r.cumulativeTarget())).append("원\n");
        sb.append("실제로 모은 금액 (누적): ").append(money(r.actualCumulativeSavings())).append("원\n");

        if (isPositive(r.cumulativeShortfall())) {
            sb.append("계획보다 덜 모인 금액 (누적): ")
                    .append(money(r.cumulativeShortfall())).append("원").append("\n");
        }
        else {
            sb.append("계획보다 덜 모인 금액 (누적): 없음\n");
        }
    }

    /**
     * "이 항목만 줄였을 때" 와 "다 줄였을 때" 를 함께 준다. 하나만 주면 모델이 둘을 인과로 이어
     * "쇼핑을 66,567원 줄이면 최대 1,210,990원" 같은 틀린 문장을 만든다. 최대예상저축액은
     * 모든 항목을 줄인 값이지 한 항목만 줄인 결과가 아니다.
     */
    private void appendSavingTip(StringBuilder sb, GoalDiagnosisResponse r) {
        if (r.topSavingCategory() != null && isPositive(r.topSavingAmount())) {
            sb.append("줄이면 효과가 가장 큰 항목: ").append(r.topSavingCategory())
                    .append(" (한 달에 ").append(money(r.topSavingAmount())).append("원까지 줄일 수 있음)")
                    .append("\n");
            sb.append(r.topSavingCategory()).append("만 줄였을 때 예상 저축액: ")
                    .append(money(r.currentExpectedSaving().add(r.topSavingAmount())))
                    .append("원").append("\n");
        }

        // 판정과 무관하게 항상 알려준다. "불가능" 일 때 특히 필요한 값이다.
        sb.append("모든 항목을 줄였을 때 예상 저축액: ")
                .append(money(r.maxExpectedSaving())).append("원").append("\n");

        if (r.savingByCategory().isEmpty()) {
            sb.append("항목별로 더 줄일 여력은 없습니다.\n");
        } else {
            sb.append("항목별 절감 여력 (줄일 수 있는 금액이 큰 순):\n");
            for (CategorySavingPotential c : r.savingByCategory()) {
                sb.append("- ").append(c.category()).append(": 한 달에 ")
                        .append(money(c.amount())).append("원까지 줄일 수 있음\n");
            }
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }
    private String money(int value) {
        return MONEY.format(value);
    }
}
