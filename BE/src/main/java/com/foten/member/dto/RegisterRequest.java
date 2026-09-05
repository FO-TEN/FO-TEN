package com.foten.member.dto;

public record RegisterRequest(
        String loginId,
        String password,
        String name,
        String nationality,
        String languageCode
) {
}
