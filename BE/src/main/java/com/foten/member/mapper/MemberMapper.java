package com.foten.member.mapper;

import com.foten.member.domain.Member;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

public interface MemberMapper {
    Optional<Member> findByLoginId(@Param("loginId") String loginId);
    boolean existsByLoginId(@Param("loginId") String loginId);
    void insert(Member member);
}
