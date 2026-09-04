package com.foten.exchange.batch;

import com.foten.common.notify.SlackNotifier;
import com.foten.exchange.dto.RefreshResult;
import com.foten.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateBatch {
    private static final long STARTUP_DELAY_MS = 15_000L;
    private static final long NEVER_AGAIN_MS = 365L * 24 * 60 * 60 * 1000;
    private final ExchangeRateService exchangeRateService;

    private final SlackNotifier slackNotifier;

    @Scheduled(cron = "${exchange.rate.cron}")
    public void collectDaily() {
        collect("정기 배치");
    }

    // 서버가 배치 시각에 꺼져 있었으면 오늘 환율이 비어 있으므로 기동 후 한 번 확인해서 배치 동작
    @Scheduled(initialDelay = STARTUP_DELAY_MS, fixedDelay = NEVER_AGAIN_MS)
    public void collectOnStartup() {
        if(exchangeRateService.hasRateFor(LocalDate.now())) {
            log.info("오늘 환율이 이미 있어 기동 보정을 건너뜁니다.");
            return;
        }
        collect("기동 보정");
    }

    // 배치가 실패해도 애플리케이션은 계속 작동
    // 화면은 DB의 마지막 환율로 뜸
    private void collect(String label) {
        try {
            report(label, exchangeRateService.refresh());
        }
        catch (Exception e) {
            log.error("[{}] 환율 갱신 실패: {}", label, e.getMessage(), e);
            slackNotifier.alert(label + " 환율 갱신 실패 - " + e.getMessage());
        }
    }

    private void report(String label, RefreshResult result) {
        if (result.allFailed()) {
            log.error("[{}] 환율을 하나도 저장하지 못했습니다. 실패 {}", label, result.failed());
            slackNotifier.alert(label + " 환율을 하나도 저장하지 못했습니다. 통화 " + result.failed());
        }
        else if (result.hasFailure()) {
            log.warn("[{}] 환율 일부 실패. 저장 {} / 실패 {}", label, result.saved(), result.failed());
            slackNotifier.alert(label + " 환율을 일부 저장하지 못했습니다. 통화 " + result.failed());
        }
        else {
            log.info("[{}] 환율 {}종 저장 (기준일 {})", label, result.saved().size(), result.baseDate());
        }
    }
}
