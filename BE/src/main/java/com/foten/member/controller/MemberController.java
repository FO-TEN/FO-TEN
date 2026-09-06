package com.foten.member.controller;

import com.foten.member.dto.MyInfoResponse;
import com.foten.member.service.MemberService;
import com.foten.member.support.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/api/users/me")
    public ResponseEntity<MyInfoResponse> me(@LoginMember long memberId) {
        return ResponseEntity.ok(memberService.getMyInfo(memberId));
    }
}