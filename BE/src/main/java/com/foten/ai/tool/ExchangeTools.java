package com.foten.ai.tool;

import com.foten.exchange.dto.KrwConversionResponse;
import com.foten.exchange.service.ExchangeRateService;
import com.foten.goal.domain.Goal;
import com.foten.goal.mapper.GoalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExchangeTools implements ToolProvider{

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    private final GoalMapper goalMapper;
    private final ExchangeRateService exchangeRateService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(ToolSpec.noArgs(
                "getGoalAmountInKrw",
                """
                회원의 목표 금액을 한국 원화로 환산해 알려줍니다.
                목표 금액은 본국 통화로 저장돼 있어 원화로 얼마인지 따로 계산해야 합니다.
                사용자가 목표 금액·환율·"한국 돈으로 얼마"를 물을 때 사용합니다.
                """,
                (arguments, context) -> describe(context.memberId())));
    }

    private String describe(long memberId) {
        Goal goal = goalMapper.selectByMemberId(memberId).orElse(null);

        if(goal == null || goal.getTargetAmount() == null || goal.getTargetCurrency() == null) {
            return "목표 금액이 아직 등록되지 않았습니다.";
        }

        KrwConversionResponse converted = exchangeRateService.toKrw(goal.getTargetCurrency(), goal.getTargetAmount());

        StringBuilder sb = new StringBuilder();
        sb.append("[목표 금액 환산]\n");
        sb.append("목표 금액: ").append(money(converted.foreignAmount()))
                .append(" ").append(converted.currencyCode()).append("\n");
        sb.append("한국 원화로: ").append(money(converted.krwAmount())).append("원").append("\n");
        sb.append("적용 환율: 1원 = ").append(converted.rate())
                .append(" ").append(converted.currencyCode()).append("\n");

        appendBaseDate(sb, converted);

        sb.append("위 금액들을 직접 계산하지 마세요. 필요한 값은 이미 위에 있습니다.");
        return sb.toString();
    }

    // 오늘 기준 환율이 아니면 그 사실을 사용자에게 고지한다.
    private void appendBaseDate(StringBuilder sb, KrwConversionResponse converted) {
        sb.append("환율 기준일: ").append(converted.baseDate());
        if (converted.stale()) {
            sb.append(" (오늘 환율이 아직 들어오지 않아 이 날짜 기준으로 계산했습니다. 답변에 기준일을 반드시 밝히세요.)");
        }
        sb.append("\n");
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : MONEY.format(value);
    }
}
