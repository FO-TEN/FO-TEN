package com.foten.exchange.mapper;

import com.foten.exchange.domain.ExchangeRateVO;

import java.util.List;
import java.util.Optional;

public interface ExchangeRateMapper {

    // 저장할 통화를 goal에서 역으로 찾는다.
    List<String> findTargetCurrencies();
    void upsert(ExchangeRateVO exchangeRate);

    Optional<ExchangeRateVO> findLatest(String currencyCode);
}
