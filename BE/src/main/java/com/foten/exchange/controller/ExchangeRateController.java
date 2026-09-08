package com.foten.exchange.controller;

import com.foten.exchange.dto.ExchangeRateResponse;
import com.foten.exchange.dto.KrwConversionResponse;
import com.foten.exchange.dto.RefreshResult;
import com.foten.exchange.service.ExchangeRateService;
import com.foten.member.support.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 환율은 회원마다 다른 값이 아니라서 memberId 를 쓰지 않는다. 그래도 @LoginMember 를 받는 것은
 * 이 앱에 인터셉터·필터 인증이 없어 그 표식이 로그인을 요구하는 유일한 방법이기 때문이다.
 *
 * 로그인을 거는 이유가 조회와 갱신이 서로 다르다.
 * 조회 : 환율 자체는 공개 정보지만, 인증 없이 열어두면 우리 서버가 남의 무료 환율 API 를
 *        대신 중계하는 꼴이 된다. 그 API 는 재배포를 금지한다.
 * 갱신 : 부를 때마다 외부 API 를 호출한다. 열어두면 외부인이 반복 호출로 우리 IP 에 429 를
 *        유발해 새벽 정기 배치를 실패시킬 수 있다.
 */
@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/{currencyCode}")
    public ExchangeRateResponse findLatest(@LoginMember long memberId, @PathVariable String currencyCode) {
        exchangeRateService.ensureRate(currencyCode);
        return exchangeRateService.findLatest(currencyCode);
    }

    @GetMapping("/{currencyCode}/krw")
    public KrwConversionResponse toKrw(
            @LoginMember long memberId,
            @PathVariable String currencyCode,
            @RequestParam BigDecimal amount
    ) {
        exchangeRateService.ensureRate(currencyCode);
        return exchangeRateService.toKrw(currencyCode, amount);
    }

    // 운영용 수동 갱신. 화면은 부르지 않고 정기 배치가 하루 한 번 같은 일을 한다.
    @PostMapping("/refresh")
    public RefreshResult refresh(@LoginMember long memberId) {
        return exchangeRateService.refresh();
    }
}