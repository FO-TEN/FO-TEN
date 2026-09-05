package com.foten.member.service;

import com.foten.member.domain.Member;
import com.foten.member.dto.RegisterRequest;

public interface MemberService {
    Member login(String loginId, String rawPassword);
    Member register(RegisterRequest request);
}
