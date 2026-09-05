package com.foten.member.service;

import com.foten.member.domain.Member;

public interface MemberService {
    Member login(String loginId, String rawPassword);
}
