package com.foten.product.domain;

import java.math.BigDecimal;
import java.util.List;

// GET /api/roadmap/segments/current/detail(4-8) 결과 — "1년차 자세히" 확대 카드(§11-5, §11-6).
public record SegmentDetail(int segmentNo, String deficitChoice, BigDecimal baselineAmount, List<Bar> bars) {
    public record Bar(String label, String type, BigDecimal amount) { // type: ACTUAL / PLAN / FUTURE
    }
}
