package com.foten.product.service;

import com.foten.product.domain.RateConditionVO;
import com.foten.product.domain.RoadmapStatus;
import java.util.List;

public interface RoadmapQueryService {
    RoadmapStatus getStatus(long memberId);

    List<RateConditionVO> getRateConditions();
}
