package com.foten.saving.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 골격(빌드 + 서버 기동) 확인용 헬스체크. 목표진단 도메인의 실제 계산 로직은
 * 별도 작업에서 이 컨트롤러 아래에 추가한다.
 */
@RestController
public class SavingController {

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
