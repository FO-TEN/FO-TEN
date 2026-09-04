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
            log.error("툴 실행 실패: {}", name, e);
            return "요청하신 정보를 가져오지 못했습니다.";
        }
    }
}
