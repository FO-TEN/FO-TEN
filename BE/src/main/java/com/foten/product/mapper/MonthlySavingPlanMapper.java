package com.foten.product.mapper;

import com.foten.product.domain.MonthlySavingPlanVO;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthlySavingPlanMapper {
    // cycle_no=1 행. "최초 현재 누적자금"을 담고 있어 목표저축액(KRW) 역산에 쓴다.
    Optional<MonthlySavingPlanVO> selectFirst(@Param("savingsRoadmapId") Long savingsRoadmapId);

    // 이번 달(planMonth)이 이미 커밋됐는지 확인 — §4-6 멱등성 체크(재호출 시 재계산 없이
    // 기존 값 그대로 반환). uk_monthly_saving_plan_roadmap_month 유니크 키와 짝을 이룬다.
    Optional<MonthlySavingPlanVO> selectByRoadmapAndMonth(
            @Param("savingsRoadmapId") Long savingsRoadmapId, @Param("planMonth") LocalDate planMonth);

    // 이 구간이 시작될 때 처음 만들어진 회차 (구간 기준 첫 회차 — 위 selectFirst 는 로드맵 전체
    // 기준 1회차라 다르다). 배분이 아니라 deficitChoice/requiredSnapshot 확인용 — §4-7 "상품 구성
    // 기준액"이 목표기준액인지 필요저축액인지 판단할 때 쓴다.
    Optional<MonthlySavingPlanVO> selectFirstBySegment(@Param("segmentId") Long segmentId);

    // 그 구간에서 가장 최근에 커밋된 회차 — §4-7 그래프가 "앞으로 유지할 구성 기준액"을
    // 판단할 때 쓴다(SPREAD로 확정된 적이 있으면 그 필요저축액이 영구 기준이 된다).
    // selectFirstBySegment 와 반대로 cycle_no DESC 로 가장 최근 값을 가져온다.
    Optional<MonthlySavingPlanVO> selectLatestBySegment(@Param("segmentId") Long segmentId);

    // 생성된 monthly_saving_plan_id 를 plan.monthlySavingPlanId 에 다시 채워 넣는다.
    void insert(MonthlySavingPlanVO plan);
}
