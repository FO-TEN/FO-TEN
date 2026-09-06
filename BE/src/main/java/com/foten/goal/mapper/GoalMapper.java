package com.foten.goal.mapper;

import com.foten.goal.domain.Goal;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoalMapper {
    Optional<Goal> selectByMemberId(@Param("memberId") Long memberId);

    void upsert(Goal goal);

    // 필요저축액은 product 도메인이 매번 재계산해서 갱신하는 유일한 쓰기 경로다(§2-4).
    // target_baseline_amount 는 여기서 절대 건드리지 않는다.
    void updateMonthlyRequiredSaving(@Param("memberId") Long memberId, @Param("amount") BigDecimal amount);
}
