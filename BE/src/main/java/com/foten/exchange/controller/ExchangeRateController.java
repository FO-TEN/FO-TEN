package com.foten.exchange.controller;

import com.foten.exchange.dto.RefreshResult;
import com.foten.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @PostMapping("/refresh")
    public RefreshResult refresh() {
        return exchangeRateService.refresh();
    }
}