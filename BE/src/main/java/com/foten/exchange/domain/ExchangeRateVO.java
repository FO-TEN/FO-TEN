package com.foten.exchange.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ExchangeRateVO {
    private Long exchangeRateId;
    private LocalDate baseDate;
    private String currencyCode;
    private BigDecimal rate;    // 1 KRW 당 해당 통화 금액
    private LocalDateTime fetchedAt;
}
