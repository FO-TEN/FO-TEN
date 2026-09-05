package com.foten.product.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// roadmap_segment 테이블과 1:1 대응하는 VO — 운용구간(예금 롤오버 1사이클)
// 시작된 구간(과거+현재)만 행으로 존재한다. 미래 구간은 저장하지 않고 조회 시 계산한다.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapSegmentVO {
    private Long segmentId;
    private Long savingsRoadmapId;
    private Integer segmentNo;       // 로드맵 내 순번 (1부터)
    private Integer plannedMonths;   // 일반 구간 12, 마지막 구간 1~23
    private LocalDate startDate;
    private LocalDate endDate;       // 구간 종료(예금 만기)일
    private Boolean isLastSegment;
    private String status;           // ACTIVE / COMPLETED
    private LocalDateTime createdAt;
}
