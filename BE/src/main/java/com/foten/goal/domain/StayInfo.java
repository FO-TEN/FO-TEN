package com.foten.goal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// stay_info 테이블과 1:1 대응하는 VO
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StayInfo {
    private Long memberId;
    private String visaType;               // 예: E-9
    private LocalDate entryDate;           // 입국일
    private LocalDate expectedReturnDate;  // 귀국 예정일 — D-day 산출 기준
    private LocalDateTime updatedAt;
}
