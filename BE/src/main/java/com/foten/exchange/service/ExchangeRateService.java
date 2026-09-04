package com.foten.exchange.service;

import com.foten.exchange.dto.RefreshResult;

import java.time.LocalDate;

public interface ExchangeRateService {
    RefreshResult refresh();
    boolean hasRateFor(LocalDate baseDate);
}
