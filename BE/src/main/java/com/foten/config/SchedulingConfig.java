package com.foten.config;

import org.springframework.context.annotation.Configuration;

/**
 * 환율 배치 스케줄러 자리. 실제 배치 로직(수출입은행 API 호출)은
 * com.foten.exchange 도메인에서 @Scheduled(cron = "${exchange.rate.cron}")
 * 형태로 구현한다. AppConfig 의 @EnableScheduling 이 이를 가능하게 한다.
 *
 * EXCHANGE_RATE_CRON 환경변수는 application-{profile}.properties 의
 * exchange.rate.cron 프로퍼티로 매핑된다.
 */
@Configuration
public class SchedulingConfig {
}
