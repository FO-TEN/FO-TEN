package com.foten.product.controller;

import com.foten.member.support.LoginMember;
import com.foten.product.domain.RateConditionAnswer;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.dto.RateConditionResponse;
import com.foten.product.dto.RateConditionResponsesRequest;
import com.foten.product.dto.SegmentCompositionResponse;
import com.foten.product.service.RoadmapCommandService;
import com.foten.product.service.RoadmapQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RateConditionController {

    private final RoadmapQueryService roadmapQueryService;
    private final RoadmapCommandService roadmapCommandService;

    @GetMapping("/api/rate-conditions")
    public ResponseEntity<List<RateConditionResponse>> list() {
        List<RateConditionResponse> conditions = roadmapQueryService.getRateConditions().stream()
                .map(RateConditionResponse::from)
                .toList();
        return ResponseEntity.ok(conditions);
    }

    @PostMapping("/api/rate-conditions/responses")
    public ResponseEntity<SegmentCompositionResponse> submitResponses(
            @LoginMember long memberId,
            @RequestBody RateConditionResponsesRequest request
    ) {
        List<RateConditionAnswer> answers = request.responses().stream()
                .map(item -> new RateConditionAnswer(item.conditionCode(), item.willMeet()))
                .toList();
        SegmentComposition composition =
                roadmapCommandService.submitRateConditionResponses(memberId, answers, request.deficitChoice());
        return ResponseEntity.ok(SegmentCompositionResponse.from(composition));
    }
}
