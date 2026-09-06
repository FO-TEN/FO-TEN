package com.foten.member.controller;

import com.foten.member.domain.Member;
import com.foten.member.dto.LoginRequest;
import com.foten.member.dto.MemberResponse;
import com.foten.member.dto.RegisterRequest;
import com.foten.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    public static final String MEMBER_ID = "memberId";
    private final MemberService memberService;

    @PostMapping("/login")
    public ResponseEntity<MemberResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        Member member = memberService.login(request.loginId(), request.password());
        startSession(servletRequest, member.getMemberId());

        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<MemberResponse> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        Member member = memberService.register(request);
        // 가입 직후 바로 온보딩으로 넘어간다. 로그인 화면으로 되돌리지 않는다.
        startSession(servletRequest, member.getMemberId());

        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member));
    }

    private void startSession(HttpServletRequest servletRequest, Long memberId) {
        servletRequest.getSession().invalidate();
        servletRequest.getSession(true).setAttribute(MEMBER_ID, memberId);
    }
}
