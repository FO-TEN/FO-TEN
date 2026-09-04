package com.foten.exchange.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeRateApiResponse (
        String result,
        @JsonProperty("base_code") String baseCode,
        @JsonProperty("time_last_update_utc") String lastUpdateUtc,
        Map<String, BigDecimal> rates
){
}
