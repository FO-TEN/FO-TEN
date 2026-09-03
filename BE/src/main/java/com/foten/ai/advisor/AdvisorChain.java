package com.foten.ai.advisor;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvisorChain {
    private final List<Advisor> advisors;
    private final int index;
    private final Function<ChatContext, String> terminal;

    public static AdvisorChain of(List<Advisor> advisors, Function<ChatContext, String> terminal) {
        return new AdvisorChain(advisors, 0, terminal);
    }

    public String next(ChatContext ctx) {
        if(index >= advisors.size()) {
            return terminal.apply(ctx);
        }

        return advisors.get(index).around(ctx, new AdvisorChain(advisors, index+1, terminal));
    }
}
