package com.foten.product.mapper;

import com.foten.product.domain.MonthlySavingPlanVO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthlySavingPlanMapper {
    // cycle_no=1 행. "최초 현재 누적자금"을 담고 있어 목표저축액(KRW) 역산에 쓴다.
    Optional<MonthlySavingPlanVO> selectFirst(@Param("savingsRoadmapId") Long savingsRoadmapId);

    // 이 구간이 시작될 때 처음 만들어진 회차 (구간 기준 첫 회차 — 위 selectFirst 는 로드맵 전체
    // 기준 1회차라 다르다). 배분이 아니라 deficitChoice/requiredSnapshot 확인용 — §4-7 "상품 구성
    // 기준액"이 목표기준액인지 필요저축액인지 판단할 때 쓴다.
    Optional<MonthlySavingPlanVO> selectFirstBySegment(@Param("segmentId") Long segmentId);

    // 생성된 monthly_saving_plan_id 를 plan.monthlySavingPlanId 에 다시 채워 넣는다.
    void insert(MonthlySavingPlanVO plan);
}
