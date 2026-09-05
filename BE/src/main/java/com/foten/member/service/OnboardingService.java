package com.foten.member.service;

import com.foten.member.dto.OnboardingRequest;
import com.foten.member.dto.OnboardingResponse;
import com.foten.member.dto.OnboardingStatusResponse;

public interface OnboardingService {
    OnboardingResponse register(long memberId, OnboardingRequest request);
    OnboardingStatusResponse getStatus(long memberId);
}
