package com.foten.exchange.service;

import com.foten.common.ExternalApiException;
import com.foten.exchange.client.ExchangeRateClient;
import com.foten.exchange.domain.ExchangeRateVO;
import com.foten.exchange.dto.RefreshResult;
import com.foten.exchange.mapper.ExchangeRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService{

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5_000L;

    private final ExchangeRateClient exchangeRateClient;
    private final ExchangeRateMapper exchangeRateMapper;

    @Override
    public RefreshResult refresh() {
        LocalDate today = LocalDate.now();
        List<String> targets = exchangeRateMapper.findTargetCurrencies();

        if (targets.isEmpty()) {
            log.warn("목표 통화가 없어 환율을 저장하지 않습니다.");
            return new RefreshResult(today, List.of(), List.of());
        }

        Map<String, BigDecimal> rates = fetchWithRetry();
        List<String> saved = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String currencyCode : targets) {
            if (save(currencyCode, rates.get(currencyCode), today)) {
                saved.add(currencyCode);
            } else {
                failed.add(currencyCode);
            }
        }
        return new RefreshResult(today, saved, failed);
    }

    // 한 통화가 실패해도 나머지는 저장한다. upsert 라 다음 실행이 덮어쓴다.
    private boolean save(String currencyCode, BigDecimal rate, LocalDate baseDate) {
        if (rate == null) {
            log.warn("환율 응답에 {} 가 없습니다.", currencyCode);
            return false;
        }
        try {
            exchangeRateMapper.upsert(ExchangeRateVO.builder()
                    .baseDate(baseDate)
                    .currencyCode(currencyCode)
                    .rate(rate)
                    .build());
            return true;
        }
        catch (DataAccessException e) {
            log.warn("{} 환율 저장 실패: {}", currencyCode, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasRateFor(LocalDate baseDate) {
        return exchangeRateMapper.countByBaseDate(baseDate) > 0;
    }

    private Map<String, BigDecimal> fetchWithRetry() {
        ExternalApiException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return exchangeRateClient.fetchRates();
            } catch (ExternalApiException e) {
                lastFailure = e;
                log.warn("환율 조회 실패 ({}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }
        throw lastFailure;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("환율 재시도가 중단되었습니다.", e);
        }
    }
}
