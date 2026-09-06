package com.foten.member.dto;

import com.foten.goal.domain.FinancialInfo;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.StayInfo;
import com.foten.member.domain.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MyInfoResponse(MemberResponse member, Residence residence, Finance finance, GoalInfo goal) {

    public record Residence(LocalDate entryDate, LocalDate expectedReturnDate) {}

    public record Finance(BigDecimal monthlyIncome, BigDecimal monthlyLivingCost,
                          BigDecimal monthlyRemittance, BigDecimal currentSavings) {}

    public record GoalInfo(BigDecimal targetAmount, String targetCurrency,
                           BigDecimal targetBaselineAmount, LocalDateTime createdAt) {}

    public static MyInfoResponse of(Member m, StayInfo s, FinancialInfo f, Goal g) {
        return new MyInfoResponse(
                MemberResponse.from(m),
                s == null ? null : new Residence(s.getEntryDate(), s.getExpectedReturnDate()),
                f == null ? null : new Finance(f.getMonthlyIncome(), f.getMonthlyLivingCost(),
                        f.getMonthlyRemittance(), f.getCurrentSavings()),
                g == null ? null : new GoalInfo(g.getTargetAmount(), g.getTargetCurrency(),
                        g.getTargetBaselineAmount(), g.getCreatedAt())
        );
    }
}