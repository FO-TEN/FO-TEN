package com.foten.ai.mapper;

import com.foten.ai.domain.MemberProfile;

import java.util.Optional;

public interface MemberProfileMapper {
    Optional<MemberProfile> findProfile(long memberId);
}
