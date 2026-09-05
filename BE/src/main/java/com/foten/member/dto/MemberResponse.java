package com.foten.member.dto;

import com.foten.member.domain.Member;

public record MemberResponse(
        long memberId,
        String loginId,
        String name,
        String nationality,
        String languageCode
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getMemberId(),
                member.getLoginId(),
                member.getName(),
                member.getNationality(),
                member.getLanguageCode()
        );
    }
}
