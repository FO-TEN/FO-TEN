package com.foten.member.dto;

public record OnboardingStatusResponse(
        boolean completed,
        boolean hasResidence,
        boolean hasFinance,
        boolean hasGoal
) {
    public static OnboardingStatusResponse of(boolean hasResidence, boolean hasFinance, boolean hasGoal) {
        return new OnboardingStatusResponse(
                hasResidence && hasFinance && hasGoal,
                hasResidence,
                hasFinance,
                hasGoal
        );
    }
}
