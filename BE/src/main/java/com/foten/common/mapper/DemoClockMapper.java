package com.foten.common.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DemoClockMapper {
    // demo_clock 에 행이 있는 회원만 값이 있다. 없으면 실제 오늘을 쓴다 (DemoClock).
    Optional<LocalDate> findToday(@Param("memberId") long memberId);
}
