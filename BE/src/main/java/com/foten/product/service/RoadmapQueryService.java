package com.foten.product.service;

import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SegmentComposition;
import java.util.List;

public interface RoadmapQueryService {
    RoadmapStatus getStatus(long memberId);

    List<RateConditionVO> getRateConditions();

    // "추천 조합 상세" — 이 구간 동안 유지할 배분 기준을 보여준다. 저장된 배분을 읽지 않고
    // RoadmapCalculationService.allocate() 로 매번 다시 계산한다 (§4-7).
    SegmentComposition getCurrentComposition(long memberId);
}
