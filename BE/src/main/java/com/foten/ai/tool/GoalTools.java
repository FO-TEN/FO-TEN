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
        sb.append("지금 소비 속도로 예상되는 저축액: ")
                .append(money(r.currentExpectedSaving())).append("원").append("\n");

        appendGap(sb, r);
        appendSavingTip(sb, r);
        appendShortfall(sb, r);

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

    // 이번 달이 아니라 목표를 세운 뒤 누적이다. "밀린 금액" 만으로는 이번 달 얘기로 읽힌다.
    private void appendShortfall(StringBuilder sb, GoalDiagnosisResponse r) {
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
        sb.append("항목별 절감 가능액은 이 결과에 없습니다. 다른 항목을 물으면 아직 알려드릴 수 없다고 답하세요.\n");
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }
}
