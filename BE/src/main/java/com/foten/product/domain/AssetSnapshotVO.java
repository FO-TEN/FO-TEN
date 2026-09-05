package com.foten.product.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// asset_snapshot 테이블과 1:1 대응하는 VO — 마감된 달의 실제 자산 상태 스냅샷
// (로드맵 그래프 과거 구간의 유일한 출처). 기록 후 UPDATE 하지 않는다.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetSnapshotVO {
    private Long assetSnapshotId;
    private Long savingsRoadmapId;
    private Long segmentId;
    private LocalDate snapshotMonth;     // 마감된 월 YYYY-MM-01
    private BigDecimal monthlyPayment;   // 그 달 실제 적금 납입 합계 — 막대 높이
    private BigDecimal cashSavingBalance; // 월말 확정 실제 현금성 저축액
    private LocalDateTime createdAt;
}
