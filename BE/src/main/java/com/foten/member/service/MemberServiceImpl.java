package com.foten.member.service;

import com.foten.common.UnauthorizedException;
import com.foten.member.domain.Member;
import com.foten.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService{

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private final MemberMapper memberMapper;

    @Override
    public Member login(String loginId, String rawPassword) {
        if(loginId == null || rawPassword == null) {
            throw new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        Member member = memberMapper.findByLoginId(loginId).orElse(null);

        if(member == null || !ENCODER.matches(rawPassword, member.getPassword())) {
            log.warn("로그인 실패");
            throw new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return member;
    }
}
