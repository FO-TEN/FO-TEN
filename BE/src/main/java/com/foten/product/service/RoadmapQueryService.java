package com.foten.product.service;

import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RoadmapGraph;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.domain.SegmentDetail;
import java.util.List;

public interface RoadmapQueryService {
    RoadmapStatus getStatus(long memberId);

    List<RateConditionVO> getRateConditions();

    // "추천 조합 상세" — 이 구간 동안 유지할 배분 기준을 보여준다. 저장된 배분을 읽지 않고
    // RoadmapCalculationService.allocate() 로 매번 다시 계산한다 (§4-7).
    SegmentComposition getCurrentComposition(long memberId);

    // 전체 로드맵 그래프(§11). 과거·현재 구간은 실제 데이터, 미래 구간은 "지금 조건이 계속
    // 유지된다"는 가정으로 재귀 시뮬레이션한다 — 미래 금리·상품은 예측하지 않는다.
    RoadmapGraph getGraph(long memberId);

    // "1년차 자세히" 확대 카드(§4-8) — 지난달(실적)/이번달(확정 계획)/다음달부터(구성 기준액)
    // 세 막대. 이번 달 저축 방식이 아직 확정(4-6) 안 됐으면 볼 의미가 없어 예외를 던진다.
    SegmentDetail getSegmentDetail(long memberId);
}
