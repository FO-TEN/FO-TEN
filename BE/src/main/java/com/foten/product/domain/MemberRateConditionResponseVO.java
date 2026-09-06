package com.foten.product.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// member_rate_condition_response 테이블과 1:1 대응하는 VO — 사용자 우대조건 응답 (최신값만 유지)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRateConditionResponseVO {
    private Long memberId;
    private String conditionCode;
    private Boolean willMeet;           // 향후 충족 예정 응답
    private LocalDateTime respondedAt;
}
