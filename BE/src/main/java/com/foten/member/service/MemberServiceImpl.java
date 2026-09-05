package com.foten.member.service;

import com.foten.common.ConflictException;
import com.foten.common.InvalidRequestException;
import com.foten.common.UnauthorizedException;
import com.foten.member.domain.Member;
import com.foten.member.dto.RegisterRequest;
import com.foten.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService{

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private final MemberMapper memberMapper;

    private static final int MAX_LOGIN_ID = 50;
    private static final int MAX_NAME = 50;
    private static final int MAX_NATIONALITY = 30;
    private static final int MAX_LANGUAGE_CODE = 10;
    private static final int MIN_PASSWORD = 8;

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

    @Override
    public Member register(RegisterRequest request) {
        validate(request);

        if (memberMapper.existsByLoginId(request.loginId())) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }

        Member member = Member.builder()
                .loginId(request.loginId())
                .password(ENCODER.encode(request.password()))
                .name(request.name())
                .nationality(request.nationality())
                .languageCode(request.languageCode())
                .build();

        try {
            memberMapper.insert(member);
        }
        catch (DuplicateKeyException e) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }
        return member;
    }

    private void validate(RegisterRequest request) {
        requireText(request.loginId(), MAX_LOGIN_ID, "아이디");
        requireText(request.name(), MAX_NAME, "이름");
        requireText(request.nationality(), MAX_NATIONALITY, "국적");
        requireText(request.languageCode(), MAX_LANGUAGE_CODE, "언어");

        if (request.password() == null || request.password().length() < MIN_PASSWORD) {
            throw new InvalidRequestException("비밀번호는 " + MIN_PASSWORD + "자 이상이어야 합니다.");
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(field + " 입력이 필요합니다.");
        }
        if (value.length() > maxLength) {
            throw new InvalidRequestException(field + " 길이가 너무 깁니다. (최대 " + maxLength + "자)");
        }
    }

}
