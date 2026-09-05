package com.foten.ai.tool;

import com.foten.ai.llm.LlmTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolRegistry {
    private final Map<String, ToolSpec> specs = new LinkedHashMap<>();

    public ToolRegistry(List<ToolProvider> providers) {
        for(ToolProvider provider : providers) {
            for(ToolSpec spec: provider.tools()) {
                specs.put(spec.name(), spec);
            }
        }
    }

    public List<LlmTool> toLlmTools() {
        return specs.values().stream().map(ToolSpec::toLlmTool).toList();
    }

    public boolean isEmpty() {
        return specs.isEmpty();
    }

    public String execute(String name, String arguments, ToolContext context) {
        ToolSpec spec = specs.get(name);
        if(spec == null) {
            log.warn("알 수 없는 툴 호출: {}", name);
            return "요청하신 기능을 찾을 수 없습니다.";
        }

        try {
            return spec.executor().apply(arguments, context);
        }
        catch (RuntimeException e) {
            // 예외 메시지에 금액이나 회원 정보가 실려 올 수 있다. 종류와 발생 위치만 남긴다.
            log.error("툴 실행 실패: {} ({} at {})", name, e.getClass().getSimpleName(), origin(e));
            return "요청하신 정보를 가져오지 못했습니다.";
        }
    }

    // 스택의 첫 줄만. 클래스·메서드·줄번호라 데이터가 섞이지 않는다.
    private String origin(RuntimeException e) {
        StackTraceElement[] trace = e.getStackTrace();
        return trace.length == 0 ? "위치 불명" : trace[0].toString();
    }
}
