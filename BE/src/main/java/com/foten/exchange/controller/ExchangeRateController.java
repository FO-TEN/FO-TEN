package com.foten.exchange.controller;

import com.foten.exchange.dto.ExchangeRateResponse;
import com.foten.exchange.dto.KrwConversionResponse;
import com.foten.exchange.dto.RefreshResult;
import com.foten.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/{currencyCode}")
    public ExchangeRateResponse findLatest(@PathVariable String currencyCode) {
        return exchangeRateService.findLatest(currencyCode);
    }

    @GetMapping("/{currencyCode}/krw")
    public KrwConversionResponse toKrw(
            @PathVariable String currencyCode,
            @RequestParam BigDecimal amount
    ) {
        return exchangeRateService.toKrw(currencyCode, amount);
    }

    @PostMapping("/refresh")
    public RefreshResult refresh() {
        return exchangeRateService.refresh();
    }
}