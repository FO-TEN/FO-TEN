package com.foten.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foten.common.ResourceNotFoundException;
import com.foten.goal.domain.CategoryMonthlySpending;
import com.foten.goal.domain.CategorySpendingInput;
import com.foten.goal.domain.FinancialInfo;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.SavingCalculationOutput;
import com.foten.goal.dto.GoalDiagnosisResponse;
import com.foten.goal.mapper.FinancialInfoMapper;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.SpendingMapper;
import com.foten.goal.mapper.TransactionSummaryMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalDiagnosisServiceImplTest {

    private static final long MEMBER_ID = 1L;

    @Mock
    private GoalMapper goalMapper;
    @Mock
    private FinancialInfoMapper financialInfoMapper;
    @Mock
    private SpendingMapper spendingMapper;
    @Mock
    private TransactionSummaryMapper transactionSummaryMapper;
    @Mock
    private SavingCalculationService savingCalculationService;

    private GoalDiagnosisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoalDiagnosisServiceImpl(
                goalMapper, financialInfoMapper, spendingMapper, transactionSummaryMapper, savingCalculationService);
    }

    // 매달 1일 기준으로 만들어야 minusMonths 계산이 달 끝 날짜(31일 등) 문제 없이 항상 정확히 3개월 전이 된다.
    private static LocalDateTime monthsAgo(int months) {
        return LocalDate.now().withDayOfMonth(1).minusMonths(months).atStartOfDay();
    }

    private static Goal 목표(BigDecimal targetBaselineAmount, BigDecimal monthlyRequiredSaving, LocalDateTime createdAt) {
        return Goal.builder()
                .goalId(1L)
                .memberId(MEMBER_ID)
                .targetAmount(BigDecimal.valueOf(420_000_000))
                .targetCurrency("VND")
                .targetBaselineAmount(targetBaselineAmount)
                .monthlyRequiredSaving(monthlyRequiredSaving)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private static FinancialInfo 재무정보() {
        return FinancialInfo.builder()
                .memberId(MEMBER_ID)
                .monthlyIncome(BigDecimal.valueOf(2_500_000))
                .monthlyLivingCost(BigDecimal.valueOf(300_000))
                .monthlyRemittance(BigDecimal.valueOf(800_000))
                .currentSavings(BigDecimal.valueOf(1_000_000))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void diagnose_goal이_없으면_ResourceNotFoundException() {
        when(goalMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.diagnose(MEMBER_ID));
    }

    @Test
    void diagnose_financialInfo가_없으면_ResourceNotFoundException() {
        when(goalMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), monthsAgo(3))));
        when(financialInfoMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.diagnose(MEMBER_ID));
    }

    @Test
    void diagnose_목표기준액_필요저축액은_goal_테이블_값을_그대로_반환한다() {
        LocalDateTime goalCreatedAt = monthsAgo(3);
        when(goalMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), goalCreatedAt)));
        when(financialInfoMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(재무정보()));
        when(spendingMapper.findCategoryMonthlySpending(eq(MEMBER_ID), anyInt())).thenReturn(List.of());
        when(spendingMapper.findCurrentTotalSpent(MEMBER_ID)).thenReturn(BigDecimal.ZERO);
        when(transactionSummaryMapper.findCumulativeSavingsPayment(eq(MEMBER_ID), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionSummaryMapper.findBalanceAsOf(eq(MEMBER_ID), any())).thenReturn(Optional.empty());
        when(transactionSummaryMapper.findAverageMonthlyExpense(eq(MEMBER_ID), any(), any(), eq(3)))
                .thenReturn(BigDecimal.ZERO);
        when(savingCalculationService.diagnose(anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SavingCalculationOutput(1_010_000, 1_057_500, null, 0, "여유있음", -10_000));

        GoalDiagnosisResponse response = service.diagnose(MEMBER_ID);

        assertEquals(BigDecimal.valueOf(1_000_000), response.monthlyBaseline());
        assertEquals(BigDecimal.valueOf(1_200_000), response.monthlyRequired());
        assertEquals(BigDecimal.valueOf(-10_000), response.additionalNeeded());
        assertEquals("여유있음", response.judgeResult());
        assertNull(response.catchUpMode());
    }

    @Test
    void diagnose_누적저축실적은_적금납입누계와_현금성저축액의_합이다() {
        LocalDateTime goalCreatedAt = monthsAgo(3); // 경과개월 = 3
        when(goalMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), goalCreatedAt)));
        when(financialInfoMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(재무정보()));
        when(spendingMapper.findCategoryMonthlySpending(eq(MEMBER_ID), anyInt())).thenReturn(List.of());
        when(spendingMapper.findCurrentTotalSpent(MEMBER_ID)).thenReturn(BigDecimal.ZERO);
        when(savingCalculationService.diagnose(anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SavingCalculationOutput(0, 0, null, 0, "불가능", 0));

        // 적금납입누계 500,000 + 현금성저축액(직전월말잔액 2,000,000 - 평균지출 400,000 = 1,600,000) = 2,100,000
        when(transactionSummaryMapper.findCumulativeSavingsPayment(eq(MEMBER_ID), eq(goalCreatedAt)))
                .thenReturn(BigDecimal.valueOf(500_000));
        when(transactionSummaryMapper.findBalanceAsOf(eq(MEMBER_ID), any()))
                .thenReturn(Optional.of(BigDecimal.valueOf(2_000_000)));
        when(transactionSummaryMapper.findAverageMonthlyExpense(eq(MEMBER_ID), any(), any(), eq(3)))
                .thenReturn(BigDecimal.valueOf(400_000));

        GoalDiagnosisResponse response = service.diagnose(MEMBER_ID);

        // 누적목표 = 목표기준액(1,000,000) x 경과개월(3) = 3,000,000
        // cumulativeShortfall = 3,000,000 - 2,100,000 = 900,000
        // achievementRate = 2,100,000 / 3,000,000 x 100 = 70.0
        assertEquals(0, BigDecimal.valueOf(900_000).compareTo(response.cumulativeShortfall()));
        assertEquals(0, new BigDecimal("70.0").compareTo(response.achievementRate()));
    }

    @Test
    void diagnose_현금성저축액은_평균지출이_잔액보다_크면_0으로_클램프된다() {
        LocalDateTime goalCreatedAt = monthsAgo(1);
        when(goalMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), goalCreatedAt)));
        when(financialInfoMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(재무정보()));
        when(spendingMapper.findCategoryMonthlySpending(eq(MEMBER_ID), anyInt())).thenReturn(List.of());
        when(spendingMapper.findCurrentTotalSpent(MEMBER_ID)).thenReturn(BigDecimal.ZERO);
        when(savingCalculationService.diagnose(anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SavingCalculationOutput(0, 0, null, 0, "불가능", 0));

        when(transactionSummaryMapper.findCumulativeSavingsPayment(eq(MEMBER_ID), eq(goalCreatedAt)))
                .thenReturn(BigDecimal.ZERO);
        // 직전월말 잔액(100,000) < 평균지출(400,000) → 현금성저축액은 음수가 아니라 0
        when(transactionSummaryMapper.findBalanceAsOf(eq(MEMBER_ID), any()))
                .thenReturn(Optional.of(BigDecimal.valueOf(100_000)));
        when(transactionSummaryMapper.findAverageMonthlyExpense(eq(MEMBER_ID), any(), any(), eq(1)))
                .thenReturn(BigDecimal.valueOf(400_000));

        GoalDiagnosisResponse response = service.diagnose(MEMBER_ID);

        // 누적목표 = 1,000,000 x 1개월 = 1,000,000, 실제누적저축액 = 0 + 0 = 0
        assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(response.cumulativeShortfall()));
        assertEquals(0, new BigDecimal("0.0").compareTo(response.achievementRate()));
    }

    @Test
    void diagnose_거래이력이_없는_회원은_직전월말_잔액을_0으로_취급한다() {
        LocalDateTime goalCreatedAt = monthsAgo(1);
        when(goalMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), goalCreatedAt)));
        when(financialInfoMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(재무정보()));
        when(spendingMapper.findCategoryMonthlySpending(eq(MEMBER_ID), anyInt())).thenReturn(List.of());
        when(spendingMapper.findCurrentTotalSpent(MEMBER_ID)).thenReturn(BigDecimal.ZERO);
        when(savingCalculationService.diagnose(anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SavingCalculationOutput(0, 0, null, 0, "불가능", 0));

        when(transactionSummaryMapper.findCumulativeSavingsPayment(eq(MEMBER_ID), eq(goalCreatedAt)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionSummaryMapper.findBalanceAsOf(eq(MEMBER_ID), any())).thenReturn(Optional.empty());
        when(transactionSummaryMapper.findAverageMonthlyExpense(eq(MEMBER_ID), any(), any(), eq(1)))
                .thenReturn(BigDecimal.ZERO);

        GoalDiagnosisResponse response = service.diagnose(MEMBER_ID);

        assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(response.cumulativeShortfall()));
    }

    @Test
    void diagnose_카테고리별_월별_지출을_CategorySpendingInput으로_조립한다() {
        LocalDateTime goalCreatedAt = monthsAgo(3);
        when(goalMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(목표(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), goalCreatedAt)));
        when(financialInfoMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.of(재무정보()));
        when(spendingMapper.findCurrentTotalSpent(MEMBER_ID)).thenReturn(BigDecimal.valueOf(300_000));
        when(transactionSummaryMapper.findCumulativeSavingsPayment(eq(MEMBER_ID), any())).thenReturn(BigDecimal.ZERO);
        when(transactionSummaryMapper.findBalanceAsOf(eq(MEMBER_ID), any())).thenReturn(Optional.empty());
        when(transactionSummaryMapper.findAverageMonthlyExpense(eq(MEMBER_ID), any(), any(), eq(3)))
                .thenReturn(BigDecimal.ZERO);
        when(savingCalculationService.diagnose(anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new SavingCalculationOutput(0, 0, null, 0, "불가능", 0));

        YearMonth currentMonth = YearMonth.now();
        List<CategoryMonthlySpending> rows = List.of(
                CategoryMonthlySpending.builder()
                        .category("식비").yearMonth(currentMonth.toString()).amount(BigDecimal.valueOf(90_000)).build(),
                CategoryMonthlySpending.builder()
                        .category("식비").yearMonth(currentMonth.minusMonths(1).toString())
                        .amount(BigDecimal.valueOf(100_000)).build(),
                CategoryMonthlySpending.builder()
                        .category("식비").yearMonth(currentMonth.minusMonths(3).toString())
                        .amount(BigDecimal.valueOf(80_000)).build());
        when(spendingMapper.findCategoryMonthlySpending(MEMBER_ID, 7)).thenReturn(rows);

        service.diagnose(MEMBER_ID);

        ArgumentCaptor<List<CategorySpendingInput>> captor = ArgumentCaptor.forClass(List.class);
        verify(savingCalculationService)
                .diagnose(anyInt(), captor.capture(), anyInt(), anyInt(), anyInt(), anyInt());

        List<CategorySpendingInput> categories = captor.getValue();
        assertEquals(1, categories.size());
        CategorySpendingInput 식비 = categories.get(0);
        assertEquals("식비", 식비.category());
        assertEquals(90_000, 식비.currentMonthSpent());
        assertEquals(LocalDate.now().getDayOfMonth(), 식비.elapsedDays());
        assertEquals(currentMonth.lengthOfMonth(), 식비.totalDays());
        // 오래된→최근 순, 갭(2개월 전)은 0으로 채워진다
        assertEquals(List.of(0, 0, 0, 80_000, 0, 100_000), 식비.last6MonthsSpending());
    }
}
