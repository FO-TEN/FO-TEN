package com.foten.exchange.service;

import com.foten.exchange.dto.ExchangeRateResponse;
import com.foten.exchange.dto.KrwConversionResponse;
import com.foten.exchange.dto.RefreshResult;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateService {
    RefreshResult refresh();
    boolean hasRateFor(LocalDate baseDate);

    ExchangeRateResponse findLatest(String currencyCode);
    KrwConversionResponse toKrw(String currencyCode, BigDecimal foreignAmount);
    KrwConversionResponse toForeign(String currencyCode, BigDecimal krwAmount);
}
