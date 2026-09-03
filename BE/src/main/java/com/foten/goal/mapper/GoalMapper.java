package com.foten.goal.mapper;

import com.foten.goal.domain.Goal;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoalMapper {
    Optional<Goal> selectByMemberId(@Param("memberId") Long memberId);
}
