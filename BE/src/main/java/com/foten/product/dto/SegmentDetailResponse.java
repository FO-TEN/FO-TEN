package com.foten.product.dto;

import com.foten.product.domain.SegmentDetail;
import java.math.BigDecimal;
import java.util.List;

public record SegmentDetailResponse(int segmentNo, String deficitChoice, BigDecimal baselineAmount, List<BarResponse> bars) {
    public record BarResponse(String label, String type, BigDecimal amount) {
    }

    public static SegmentDetailResponse from(SegmentDetail detail) {
        List<BarResponse> bars = detail.bars().stream()
                .map(b -> new BarResponse(b.label(), b.type(), b.amount()))
                .toList();
        return new SegmentDetailResponse(detail.segmentNo(), detail.deficitChoice(), detail.baselineAmount(), bars);
    }
}
