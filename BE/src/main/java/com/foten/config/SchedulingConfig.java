package com.foten.config;

import org.springframework.context.annotation.Configuration;

/**
 * 환율 배치 스케줄러 자리. 실제 배치 로직(ExchangeRate-API 무료 공개 엔드포인트
 * open.er-api.com 호출)은 com.foten.exchange 도메인에서
 * @Scheduled(cron = "${exchange.rate.cron}") 형태로 구현한다.
 * AppConfig 의 @EnableScheduling 이 이를 가능하게 한다.
 *
 * 이 API 는 환율을 쓰는 화면에 출처 표기를 요구한다 —
 * 온보딩 화면 하단의 "Rates By Exchange Rate API" 링크가 그것이다.
 * 호출처를 다른 API 로 바꾸면 그 표기도 함께 손봐야 한다.
 *
 * EXCHANGE_RATE_CRON 환경변수는 application-{profile}.properties 의
 * exchange.rate.cron 프로퍼티로 매핑된다.
 */
@Configuration
public class SchedulingConfig {
}
