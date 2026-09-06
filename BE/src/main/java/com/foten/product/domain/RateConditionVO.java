package com.foten.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// rate_condition 테이블과 1:1 대응하는 VO — 우대금리 조건 종류 마스터
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateConditionVO {
    private String conditionCode;      // PK. 예: SALARY_TRANSFER
    private String label;              // 화면 표시명
    private String description;        // 조건 상세 / 질문 문구
    private Boolean isBehaviorBased;   // true = 향후 행동으로 충족 가능 → 공통 질문 대상
}
