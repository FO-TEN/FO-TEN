package com.foten.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResponse(
        String currencyCode,
        BigDecimal rate,
        LocalDate baseDate,
        boolean stale   // 오늘 값이 아닌 경우 true
) {
}
