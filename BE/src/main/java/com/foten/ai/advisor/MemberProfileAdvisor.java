package com.foten.ai.advisor;

import com.foten.ai.domain.MemberProfile;
import com.foten.ai.llm.LlmMessage;
import com.foten.ai.mapper.MemberProfileMapper;
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

    private final MemberProfileMapper memberProfileMapper;

    @Override
    public String around(ChatContext ctx, AdvisorChain chain) {
        memberProfileMapper.findProfile(ctx.memberId())
                .map(this::describe)
                .ifPresent(profile -> ctx.messages().add(LlmMessage.system(profile)));
        return chain.next(ctx);
    }

    private String describe(MemberProfile profile) {
        StringBuilder sb = new StringBuilder("[회원 정보]\n");
        sb.append("이름: ").append(profile.getName()).append("\n");
        sb.append("국적: ").append(profile.getNationality()).append("\n");

        appendReturnDate(sb, profile.getExpectedReturnDate());

        if (profile.getTargetCurrency() != null) {
            sb.append("목표 통화: ").append(profile.getTargetCurrency()).append("\n");
        }

        appendRoadmap(sb, profile.isRoadmapExists());
        appendRateConditions(sb, profile.isRateConditionsAnswered());
        sb.append("금액은 이 정보에 없습니다. 필요하면 도구를 사용하세요.");
        return sb.toString();
    }

    // 온보딩 전이면 체류정보가 없다. 남은 기간은 서버가 센다 - 모델이 날짜를 빼면 월말·윤년에서 틀린다.
    private void appendReturnDate(StringBuilder sb, LocalDate returnDate) {
        if (returnDate == null) {
            sb.append("귀국 예정일: 아직 등록하지 않음\n");
            return;
        }

        LocalDate today = LocalDate.now();
        long monthsLeft = ChronoUnit.MONTHS.between(today, returnDate);
        long daysLeft = ChronoUnit.DAYS.between(today, returnDate);
        sb.append("귀국 예정일: ").append(returnDate)
                .append(" (약 ").append(monthsLeft).append("개월, ")
                .append(daysLeft).append("일 남음)").append("\n");
    }

    // 미리 알려주면 제출하겠다고 나섰다가 서버에 막히는 왕복이 없어진다.
    private void appendRateConditions(StringBuilder sb, boolean answered) {
        if (!answered) {
            return;
        }
        sb.append("우대조건: 이미 제출해 상품이 정해짐\n");
        sb.append("다시 제출할 수 없습니다. 상품이 궁금하다는 요청이면 getSegmentComposition 을 쓰세요.\n");
    }

    private void appendRoadmap(StringBuilder sb, boolean hasRoadmap) {
        if (hasRoadmap) {
            sb.append("저축 로드맵: 이미 있음").append("\n");
            sb.append("로드맵은 회원당 하나뿐이라 새로 만들 수 없습니다.")
                    .append(" 만들어 달라고 하면 이미 있다고 답하고 startRoadmap 을 부르지 마세요.\n");
            return;
        }
        sb.append("저축 로드맵: 아직 없음\n");
    }
}
