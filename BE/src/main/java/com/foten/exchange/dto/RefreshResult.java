package com.foten.exchange.dto;

import java.time.LocalDate;
import java.util.List;

public record RefreshResult(
        LocalDate baseDate,
        List<String> saved,
        List<String> failed
) {
    public boolean hasFailure() {
        return !failed.isEmpty();
    }

    public boolean allFailed() {
        return saved.isEmpty() && !failed.isEmpty();
    }
}
