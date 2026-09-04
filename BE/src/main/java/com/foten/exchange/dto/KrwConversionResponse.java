package com.foten.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KrwConversionResponse(
        String currencyCode,
        BigDecimal foreignAmount,
        BigDecimal krwAmount,
        BigDecimal rate,
        LocalDate baseDate,
        boolean stale
) {
}
