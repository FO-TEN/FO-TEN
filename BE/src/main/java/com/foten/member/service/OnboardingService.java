package com.foten.member.service;

import com.foten.member.dto.OnboardingRequest;
import com.foten.member.dto.OnboardingResponse;

public interface OnboardingService {
    OnboardingResponse register(long memberId, OnboardingRequest request);
}
