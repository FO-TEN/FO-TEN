package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.domain.MemberProfile;
import com.foten.ai.dto.Suggestion;
import com.foten.ai.mapper.MemberProfileMapper;
import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RoadmapStatus;
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
    private static final String CONFIRM_TOOL = "confirmMonthlySaving";
    private static final String GRAPH_TOOL = "getRoadmapGraph";
    private static final String FLOW_ONBOARDING = "ONBOARDING";
    private static final String FLOW_NEW_SEGMENT = "NEW_SEGMENT";
    private static final Suggestion RECHECK_CONDITIONS = Suggestion.ask("우대조건 다시 확인할래");

    private final MemberProfileMapper memberProfileMapper;
    private final RoadmapQueryService roadmapQueryService;

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        if (contentKo == null) {
            return List.of();
        }

        List<String> tools = ctx.calledTools();

        // 방금 그 단계를 보여준 턴에는 같은 것을 다시 권하지 않는다.
        // 코드를 옮기려고 질문을 다시 불러온 뒤 제출한 턴도 여기서 걸러진다.
        // 구간이 바뀌는 달은 방식을 정해도 아직 확정 전이다. 조건까지 받아야 끝난다.
        if (tools.contains(CONFIRM_TOOL)) {
            return isNewSegment(ctx.memberId()) ? List.of(RECHECK_CONDITIONS) : List.of();
        }
        // 방금 그 단계를 보여준 턴에는 같은 것을 다시 권하지 않는다.
        // 코드를 옮기려고 질문을 다시 불러온 뒤 제출한 턴도 여기서 걸러진다.
        // 상품이 정해진 직후에는 전체 흐름으로 이어준다. 그래프를 보여준 턴에는 더 권할 것이 없다.
        if (tools.contains(SUBMIT_TOOL) || tools.contains(COMPOSITION_TOOL)) {
            return List.of(Suggestion.ask("앞으로 어떻게 모으면 돼?"));
        }
        if (tools.contains(GRAPH_TOOL)) {
            return List.of();
        }
        // 질문을 보여준 턴에는 고를 수 있게 조건을 칩으로 낸다.
        if (tools.contains(CONDITION_TOOL)) {
            return conditionChips(ctx.memberId());
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

        List<Suggestion> deficit = deficitChips(ctx.memberId(), profile);
        if (!deficit.isEmpty()) {
            return deficit;
        }
        // 밀린 금액이 없는 구간 전환은 바로 조건 확인으로 간다.
        return isNewSegment(ctx.memberId())
                ? List.of(RECHECK_CONDITIONS)
                : List.of(Suggestion.ask("상품 구성 알려줘"));
    }

    private boolean isNewSegment(long memberId) {
        return FLOW_NEW_SEGMENT.equals(roadmapQueryService.getStatus(memberId).flowType());
    }

    // 밀린 금액이 있는 달에만 고를 것이 생긴다. 누르는 순간 이번 달 저축액이 정해지므로
    // 사용자가 직접 고른 것으로 남아야 한다.
    private List<Suggestion> deficitChips(long memberId, MemberProfile profile) {
        // 밀린 금액은 갚기 전까지 남아 있다. 확정한 달에도 계속 내면 두 번 고르게 된다.
        if (profile.isMonthlySavingConfirmed()) {
            return List.of();
        }
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        if (FLOW_ONBOARDING.equals(status.flowType()) || !Boolean.TRUE.equals(status.hasShortfall())) {
            return List.of();
        }
        return List.of(
                Suggestion.deficitChoice("이번 달에 밀린 금액까지 다 채울게요"),
                Suggestion.deficitChoice("남은 기간에 나눠서 채울게요"));
    }

    // 이미 제출했으면 도구가 질문 대신 안내를 돌려준다. 고를 것이 없으니 칩도 내지 않는다.
    private List<Suggestion> conditionChips(long memberId) {
        MemberProfile profile = memberProfileMapper.findProfile(memberId).orElse(null);
        if (profile == null || !profile.isRoadmapExists()) {
            return List.of();
        }
        // 구간이 바뀌는 달은 지난 구간 답이 남아 있어도 다시 고르게 한다.
        if (profile.isRateConditionsAnswered() && !isNewSegment(memberId)) {
            return List.of();
        }
        List<RateConditionVO> conditions = roadmapQueryService.getRateConditions();
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream()
                .map(c -> Suggestion.rateCondition(c.getConditionCode(), c.getLabel()))
                .toList();
    }
}
