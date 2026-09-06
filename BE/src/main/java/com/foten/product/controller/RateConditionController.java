package com.foten.product.controller;

import com.foten.product.dto.RateConditionResponse;
import com.foten.product.service.RoadmapQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RateConditionController {

    private final RoadmapQueryService roadmapQueryService;

    @GetMapping("/api/rate-conditions")
    public ResponseEntity<List<RateConditionResponse>> list() {
        List<RateConditionResponse> conditions = roadmapQueryService.getRateConditions().stream()
                .map(RateConditionResponse::from)
                .toList();
        return ResponseEntity.ok(conditions);
    }
}
