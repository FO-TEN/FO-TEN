package com.foten.ai.advisor;

import com.foten.common.clock.DemoClock;
import com.foten.ai.domain.MemberProfile;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.mapper.MemberProfileMapper;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 회원이 누구인지 챗봇에게 맥락 전달
// 금액은 전달하지 않고, 툴을 통해 가져오는 것으로 한다.
@Component
@Order(0)
@RequiredArgsConstructor
public class MemberProfileAdvisor implements Advisor {

    // 구간이 바뀌는 달인지에 따라 우대조건 안내가 반대가 된다.
    private static final String FLOW_NEW_SEGMENT = "NEW_SEGMENT";

    private final MemberProfileMapper memberProfileMapper;
    private final RoadmapQueryService roadmapQueryService;
    private final DemoClock demoClock;

    @Override
    public String around(ChatContext ctx, AdvisorChain chain) {
        memberProfileMapper.findProfile(ctx.memberId())
                .map(profile -> describe(ctx.memberId(), profile))
                .ifPresent(profile -> ctx.messages().add(LlmMessage.system(profile)));
        return chain.next(ctx);
    }

    private String describe(long memberId, MemberProfile profile) {
        StringBuilder sb = new StringBuilder("[회원 정보]\n");
        sb.append("이름: ").append(profile.getName()).append("\n");
        sb.append("국적: ").append(profile.getNationality()).append("\n");

        appendReturnDate(sb, profile.getExpectedReturnDate(), demoClock.today(memberId));

        if (profile.getTargetCurrency() != null) {
            sb.append("목표 통화: ").append(profile.getTargetCurrency()).append("\n");
        }

        appendRoadmap(sb, profile.isRoadmapExists());
        appendRateConditions(sb, memberId, profile);
        sb.append("금액은 이 정보에 없습니다. 필요하면 도구를 사용하세요.");
        return sb.toString();
    }

    // 온보딩 전이면 체류정보가 없다. 남은 기간은 서버가 센다 - 모델이 날짜를 빼면 월말·윤년에서 틀린다.
    private void appendReturnDate(StringBuilder sb, LocalDate returnDate, LocalDate today) {
        if (returnDate == null) {
            sb.append("귀국 예정일: 아직 등록하지 않음\n");
            return;
        }

        long monthsLeft = ChronoUnit.MONTHS.between(today, returnDate);
        long daysLeft = ChronoUnit.DAYS.between(today, returnDate);
        sb.append("귀국 예정일: ").append(returnDate)
                .append(" (약 ").append(monthsLeft).append("개월, ")
                .append(daysLeft).append("일 남음)").append("\n");
    }

    // 미리 알려주면 제출하겠다고 나섰다가 서버에 막히는 왕복이 없어진다.
    // 다만 구간이 바뀌는 달에는 다시 받아야 하므로 반대로 알려야 한다.
    private void appendRateConditions(StringBuilder sb, long memberId, MemberProfile profile) {
        if (!profile.isRateConditionsAnswered()) {
            return;
        }
        if (isNewSegment(memberId, profile)) {
            sb.append("우대조건: 지난 구간에 답한 것이 남아 있음\n");
            sb.append("구간이 바뀌는 달이라 다시 받아야 합니다.");
            sb.append(" 확인해 달라고 하면 getPreferentialConditionQuestions 를 쓰세요.\n");
            return;
        }
        sb.append("우대조건: 이미 제출해 상품이 정해짐\n");
        sb.append("다시 제출할 수 없습니다. 상품이 궁금하다는 요청이면 getSegmentComposition 을 쓰세요.\n");
    }

    private boolean isNewSegment(long memberId, MemberProfile profile) {
        if (!profile.isRoadmapExists()) {
            return false;
        }
        return FLOW_NEW_SEGMENT.equals(roadmapQueryService.getStatus(memberId).flowType());
    }

    private void appendRoadmap(StringBuilder sb, boolean hasRoadmap) {
        if (hasRoadmap) {
            sb.append("저축 로드맵: 이미 있음").append("\n");
            sb.append("로드맵은 회원당 하나뿐이라 새로 만들 수 없습니다.")
                    .append(" 만들어 달라고 하면 이미 있다고 답하고 startRoadmap 을 부르지 마세요.\n");
            // 목표기준액이 바뀌면 이미 가입한 상품 구성이 어긋나서 서버가 막는다.
            sb.append("목표 금액: 로드맵이 시작돼 더 이상 바꿀 수 없습니다.")
                    .append(" 바꿔 달라고 하면 로드맵을 시작해서 고정됐다고 알리세요.\n");
            return;
        }
        sb.append("저축 로드맵: 아직 없음\n");
        // 대화로는 못 고친다. 어디서 고치는지까지 알려야 막다른 답이 되지 않는다.
        sb.append("목표 금액: 아직 바꿀 수 있습니다.")
                .append(" 대화로는 못 바꾸니 내 정보 화면에서 수정하라고 안내하세요.\n");
    }
}
