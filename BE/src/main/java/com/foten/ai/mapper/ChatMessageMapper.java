package com.foten.ai.mapper;

import com.foten.ai.domain.ChatMessageVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatMessageMapper {
    List<ChatMessageVO> findRecent(@Param("memberId") long memberId, @Param("limit") int limit);

    void insert(ChatMessageVO message);

    void deleteByMemberId(@Param("memberId") long memberId);

    List<ChatMessageVO> findBefore(@Param("memberId") long memberId,
                                   @Param("before") Long before,
                                   @Param("size") int size
    );
}
