package com.foten.common.clock;

import com.foten.common.mapper.DemoClockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

/**
 * 회원 기준 "오늘".
 *
 * 시연 영상은 "오늘 가입한 사람의 7개월 뒤·1년 뒤" 화면을 미래 날짜 그대로 보여줘야 하는데,
 * 회차·부족액·이번 달 소비 같은 계산이 전부 LocalDate.now() 를 기준으로 돌아가서 시드 날짜만
 * 미래로 넣어서는 재현이 안 됐다. 그래서 회원별로 "오늘"을 demo_clock 테이블에 고정할 수 있게
 * 하고, 서비스 코드는 LocalDate.now() 대신 이 클래스를 거친다. 행이 없는 회원(운영·일반 시드)은
 * 실제 오늘이라 동작이 달라지지 않는다.
 *
 * SQL 안에서 CURDATE() 를 쓰던 매퍼(소비 집계·프로필)는 같은 규칙을
 * COALESCE((SELECT today FROM demo_clock WHERE member_id = ?), CURDATE()) 로 직접 적용한다.
 * 환율 배치·고시값 신선도 판단은 회원과 무관한 실제 시각이라 여기를 거치지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DemoClock {

    private final DemoClockMapper demoClockMapper;

    public LocalDate today(long memberId) {
        return demoClockMapper.findToday(memberId).orElseGet(LocalDate::now);
    }

    public YearMonth thisMonth(long memberId) {
        return YearMonth.from(today(memberId));
    }

    // 대화 저장 시각처럼 "날짜는 시연 날짜, 시각은 지금"이면 되는 곳에 쓴다.
    public LocalDateTime now(long memberId) {
        LocalDate today = today(memberId);
        return today.equals(LocalDate.now()) ? LocalDateTime.now() : today.atTime(LocalTime.now().withNano(0));
    }
}
