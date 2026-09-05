package com.foten.product.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// savings_roadmap 테이블과 1:1 대응하는 VO — 저축 로드맵 헤더 (회원당 1건)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsRoadmapVO {
    private Long savingsRoadmapId;
    private Long memberId;
    private LocalDate startDate;    // 온보딩일 (= 저축 운용 시작일)
    private LocalDate endDate;      // 예상 귀국일 - 1개월
    private Integer totalMonths;    // 최초 남은 저축 가능 개월수
    private LocalDateTime createdAt;
}
