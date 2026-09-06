package com.foten.product.service;

import com.foten.product.domain.RoadmapStatus;

public interface RoadmapQueryService {
    RoadmapStatus getStatus(long memberId);
}
