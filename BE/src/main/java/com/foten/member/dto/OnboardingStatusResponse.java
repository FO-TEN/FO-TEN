package com.foten.member.dto;

import java.time.LocalDate;
public record OnboardingStatusResponse(
        boolean completed,
        boolean hasResidence,
        boolean hasFinance,
        boolean hasGoal,
        LocalDate today   // 이 회원 기준 "오늘" (demo_clock 이 있으면 그 날짜) — 화면이 브라우저 날짜 대신 쓴다
) {
    public static OnboardingStatusResponse of(boolean hasResidence, boolean hasFinance, boolean hasGoal, LocalDate today) {
        return new OnboardingStatusResponse(
                hasResidence && hasFinance && hasGoal,
                hasResidence,
                hasFinance,
                hasGoal,
                today
        );
    }
}
