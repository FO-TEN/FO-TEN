package com.foten.spending.controller;

import com.foten.common.InvalidRequestException;
import com.foten.member.support.LoginMember;
import com.foten.spending.dto.MonthlySpendingResponse;
import com.foten.spending.service.SpendingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SpendingController {

    // 시드가 6개월치라 그보다 먼 과거는 어차피 빈 결과다. 음수는 미래라 막는다.
    private static final int MAX_MONTHS_AGO = 11;

    private final SpendingQueryService spendingQueryService;

    @GetMapping("/api/spending")
    public ResponseEntity<MonthlySpendingResponse> monthly(
            @LoginMember long memberId,
            @RequestParam(defaultValue = "0") int monthsAgo
    ) {
        if (monthsAgo < 0 || monthsAgo > MAX_MONTHS_AGO) {
            throw new InvalidRequestException("monthsAgo 는 0에서 " + MAX_MONTHS_AGO + " 사이여야 합니다.");
        }
        return ResponseEntity.ok(MonthlySpendingResponse.from(
                spendingQueryService.getMonthlySpending(memberId, monthsAgo)));
    }
}