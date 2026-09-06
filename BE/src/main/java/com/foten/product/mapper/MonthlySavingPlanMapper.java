package com.foten.product.mapper;

import com.foten.product.domain.MonthlySavingPlanVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthlySavingPlanMapper {
    // cycle_no=1 행. "최초 현재 누적자금"을 담고 있어 목표저축액(KRW) 역산에 쓴다.
    Optional<MonthlySavingPlanVO> selectFirst(@Param("savingsRoadmapId") Long savingsRoadmapId);

    // 생성된 monthly_saving_plan_id 를 plan.monthlySavingPlanId 에 다시 채워 넣는다.
    void insert(MonthlySavingPlanVO plan);
}
