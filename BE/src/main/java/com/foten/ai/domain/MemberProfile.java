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
}
