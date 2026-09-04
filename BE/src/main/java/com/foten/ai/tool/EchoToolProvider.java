package com.foten.ai.tool;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EchoToolProvider implements ToolProvider{
    @Override
    public List<ToolSpec> tools() {
        return List.of(ToolSpec.noArgs(
                "getServerTime",
                "서버의 현재 시각을 조회합니다. 사용자가 오늘 날짜나 지금 시각을 물을 때 사용합니다.",
                (arguments, context) -> "2026년 9월 4일 금요일"
        ));
    }
}
