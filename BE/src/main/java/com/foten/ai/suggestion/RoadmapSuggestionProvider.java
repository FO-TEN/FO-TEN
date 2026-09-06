package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.domain.MemberProfile;
import com.foten.ai.dto.Suggestion;
import com.foten.ai.mapper.MemberProfileMapper;
import com.foten.product.domain.RateConditionVO;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// UI 흐름 v5 의 사용자 버튼을 칩으로. 부를 도구가 있는 단계만 붙인다.
@Component
@RequiredArgsConstructor
public class RoadmapSuggestionProvider implements SuggestionProvider {

    private static final String STATUS_TOOL = "getRoadmapStatus";
    private static final String START_TOOL = "startRoadmap";
    private static final String CONDITION_TOOL = "getPreferentialConditionQuestions";
    private static final String SUBMIT_TOOL = "submitPreferentialConditions";
    private static final String COMPOSITION_TOOL = "getSegmentComposition";

    private final MemberProfileMapper memberProfileMapper;
    private final RoadmapQueryService roadmapQueryService;

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        if (contentKo == null) {
            return List.of();
        }

        List<String> tools = ctx.calledTools();

        // 질문을 보여준 턴에는 고를 수 있게 조건을 칩으로 낸다.
        if (tools.contains(CONDITION_TOOL)) {
            return conditionChips(ctx.memberId());
        }
        // 방금 그 단계를 보여준 턴에는 같은 것을 다시 권하지 않는다.
        if (tools.contains(SUBMIT_TOOL) || tools.contains(COMPOSITION_TOOL)) {
            return List.of();
        }
        if (!tools.contains(STATUS_TOOL) && !tools.contains(START_TOOL)) {
            return List.of();
        }

        // 이 시점에는 생성·제출 트랜잭션이 끝나 있다. 방금 만든 로드맵도 여기서 보인다.
        MemberProfile profile = memberProfileMapper.findProfile(ctx.memberId()).orElse(null);
        if (profile == null) {
            return List.of();
        }

        if (!profile.isRoadmapExists()) {
            return List.of(Suggestion.ask("내 로드맵 만들기"));
        }
        if (!profile.isRateConditionsAnswered()) {
            return List.of(Suggestion.ask("우대조건 확인할래"));
        }
        return List.of(Suggestion.ask("상품 구성 알려줘"));
    }

    // 이미 제출했으면 도구가 질문 대신 안내를 돌려주므로 칩도 내지 않는다.
    private List<Suggestion> conditionChips(long memberId) {
        List<RateConditionVO> conditions = roadmapQueryService.getRateConditions();
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream()
                .map(c -> Suggestion.rateCondition(c.getConditionCode(), c.getLabel()))
                .toList();
    }
}
