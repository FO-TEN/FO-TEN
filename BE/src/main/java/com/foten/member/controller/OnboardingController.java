package com.foten.member.controller;

import com.foten.member.dto.OnboardingRequest;
import com.foten.member.dto.OnboardingResponse;
import com.foten.member.service.OnboardingService;
import com.foten.member.support.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboardingService;

    @PostMapping("/api/users/me/onboarding")
    public ResponseEntity<OnboardingResponse> register(
            @LoginMember long memberId,
            @RequestBody OnboardingRequest request
    ) {
        return ResponseEntity.ok(onboardingService.register(memberId, request));
    }
}
