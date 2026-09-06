package com.foten.ai.domain;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberProfile {
    private String name;
    private String nationality;
    private LocalDate expectedReturnDate;
    private String targetCurrency;
    private boolean roadmapExists;
    private boolean rateConditionsAnswered;
    // 이번 달 저축액을 이미 확정했는가. 밀린 금액은 갚기 전까지 계속 남아 있어서
    // 이 값이 없으면 확정한 뒤에도 계속 다시 묻게 된다.
    private boolean monthlySavingConfirmed;
}
