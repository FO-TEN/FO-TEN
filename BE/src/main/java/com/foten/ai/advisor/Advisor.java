package com.foten.ai.advisor;

public interface Advisor {
    String around(ChatContext ctx, AdvisorChain chain);
}
