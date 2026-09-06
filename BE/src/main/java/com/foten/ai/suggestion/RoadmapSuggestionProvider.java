package com.foten.ai.suggestion;

import com.foten.ai.advisor.ChatContext;
import com.foten.ai.domain.MemberProfile;
import com.foten.ai.dto.Suggestion;
import com.foten.ai.mapper.MemberProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoadmapSuggestionProvider implements SuggestionProvider {

    private static final String STATUS_TOOL = "getRoadmapStatus";
    private static final String START_TOOL = "startRoadmap";
    private static final String CONDITION_TOOL = "getPreferentialConditionQuestions";

    private final MemberProfileMapper memberProfileMapper;

    @Override
    public List<Suggestion> suggest(ChatContext ctx, String contentKo) {
        List<String> tools = ctx.calledTools();
        boolean touchedRoadmap = tools.contains(STATUS_TOOL) || tools.contains(START_TOOL);

        // 우대조건 질문을 보여준 턴에는 칩을 붙이지 않는다.
        if (contentKo == null || !touchedRoadmap || tools.contains(CONDITION_TOOL)) {
            return List.of();
        }

        // 이 시점에는 생성 트랜잭션이 끝나 있다. 방금 만든 로드맵도 여기서 보인다.
        boolean roadmapExists = memberProfileMapper.findProfile(ctx.memberId())
                .map(MemberProfile::isRoadmapExists)
                .orElse(false);

        return roadmapExists
                ? List.of(Suggestion.ask("우대조건 확인할래"))
                : List.of(Suggestion.ask("내 로드맵 만들기"));
    }
}