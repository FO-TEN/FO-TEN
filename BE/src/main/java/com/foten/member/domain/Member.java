package com.foten.member.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {
    private Long memberId;
    private String loginId;
    private String password;
    private String name;
    private String nationality;
    private String languageCode;
    private LocalDateTime createdAt;
}
