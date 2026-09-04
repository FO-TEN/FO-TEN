package com.foten.ai.tool;

import com.foten.goal.dto.GoalDiagnosisResponse;
import com.foten.goal.service.GoalDiagnosisService;
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

    @Override
    public List<ToolSpec> tools() {
        return List.of(ToolSpec.noArgs(
                "diagnoseGoal",
                """
                회원의 목표 저축 달성 가능성을 진단합니다.
                목표 달성 여부, 이번 달 필요한 저축액, 지금까지 밀린 금액,
                줄이면 좋은 소비 카테고리를 알려줍니다.
                사용자가 목표 달성 여부·저축 현황·절약 방법을 물을 때 사용합니다.
                """,
                (arguments, context) -> describe(goalDiagnosisService.diagnose(context.memberId()))));
    }

    private String describe(GoalDiagnosisResponse r) {
        StringBuilder sb = new StringBuilder();

        sb.append("[목표 진단]\n");
        sb.append("판정: ").append(r.judgeResult()).append("\n");
        sb.append("이번 달 목표 저축액: ").append(money(r.monthlyBaseline())).append("원").append("\n");
        sb.append("지금 소비 속도로 예상되는 이번 달 저축액: ")
                .append(money(r.currentExpectedSaving())).append("원").append("\n");

        appendGap(sb, r);

        sb.append("최대한 아꼈을 때 가능한 이번 달 저축액: ")
                .append(money(r.maxExpectedSaving())).append("원").append("\n");

        appendShortfall(sb, r);
        appendSavingTip(sb, r);

        sb.append("달성률: ").append(r.achievementRate()).append("%\n");
        sb.append("위 금액들의 차액을 직접 빼서 구하지 마세요. 필요한 차액은 이미 위에 있습니다.");
        return sb.toString();
    }

    private void appendGap(StringBuilder sb, GoalDiagnosisResponse r) {
        BigDecimal gap = r.additionalNeeded();
        if (gap == null || gap.signum() == 0) {
            sb.append("목표까지 부족한 금액: 없음\n");
        } else if (gap.signum() > 0) {
            sb.append("목표까지 부족한 금액: ").append(money(gap)).append("원").append("\n");
        } else {
            sb.append("목표를 넘어선 여유 금액: ").append(money(gap.negate())).append("원").append("\n");
        }
    }

    private void appendShortfall(StringBuilder sb, GoalDiagnosisResponse r) {
        if (isPositive(r.cumulativeShortfall())) {
            sb.append("지금까지 밀린 금액: ").append(money(r.cumulativeShortfall())).append("원").append("\n");
        }
        else {
            sb.append("지금까지 밀린 금액: 없음\n");
        }
    }

    private void appendSavingTip(StringBuilder sb, GoalDiagnosisResponse r) {
        if (r.topSavingCategory() != null && isPositive(r.topSavingAmount())) {
            sb.append("줄이면 좋은 항목: ").append(r.topSavingCategory())
                    .append(" ").append(money(r.topSavingAmount())).append("원").append("\n");
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }
}
