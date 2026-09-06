package com.foten.product.controller;

import com.foten.member.support.LoginMember;
import com.foten.product.domain.CreatedRoadmap;
import com.foten.product.domain.DeficitChoiceResult;
import com.foten.product.domain.RoadmapGraph;
import com.foten.product.domain.RoadmapStatus;
import com.foten.product.domain.SegmentComposition;
import com.foten.product.dto.CreateRoadmapResponse;
import com.foten.product.dto.DeficitChoiceRequest;
import com.foten.product.dto.DeficitChoiceResponse;
import com.foten.product.dto.RoadmapGraphResponse;
import com.foten.product.dto.RoadmapStatusResponse;
import com.foten.product.dto.SegmentCompositionResponse;
import com.foten.product.service.RoadmapCommandService;
import com.foten.product.service.RoadmapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapQueryService roadmapQueryService;
    private final RoadmapCommandService roadmapCommandService;

    @GetMapping("/api/roadmap/status")
    public ResponseEntity<RoadmapStatusResponse> status(@LoginMember long memberId) {
        RoadmapStatus status = roadmapQueryService.getStatus(memberId);
        return ResponseEntity.ok(RoadmapStatusResponse.from(status));
    }

    @PostMapping("/api/roadmap")
    public ResponseEntity<CreateRoadmapResponse> create(@LoginMember long memberId) {
        CreatedRoadmap roadmap = roadmapCommandService.createRoadmap(memberId);
        return ResponseEntity.ok(CreateRoadmapResponse.from(roadmap));
    }

    @GetMapping("/api/roadmap/segments/current/composition")
    public ResponseEntity<SegmentCompositionResponse> currentComposition(@LoginMember long memberId) {
        SegmentComposition composition = roadmapQueryService.getCurrentComposition(memberId);
        return ResponseEntity.ok(SegmentCompositionResponse.from(composition));
    }

    @PostMapping("/api/roadmap/monthly-plan/deficit-choice")
    public ResponseEntity<DeficitChoiceResponse> confirmDeficitChoice(
            @LoginMember long memberId,
            @RequestBody(required = false) DeficitChoiceRequest request
    ) {
        String choice = request != null ? request.choice() : null;
        DeficitChoiceResult result = roadmapCommandService.confirmDeficitChoice(memberId, choice);
        return ResponseEntity.ok(DeficitChoiceResponse.from(result));
    }

    @GetMapping("/api/roadmap/graph")
    public ResponseEntity<RoadmapGraphResponse> graph(@LoginMember long memberId) {
        RoadmapGraph graph = roadmapQueryService.getGraph(memberId);
        return ResponseEntity.ok(RoadmapGraphResponse.from(graph));
    }
}
