package com.foten.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foten.common.RoadmapStateConflictException;
import com.foten.exchange.dto.KrwConversionResponse;
import com.foten.exchange.service.ExchangeRateService;
import com.foten.goal.domain.Goal;
import com.foten.goal.domain.GoalCalculationOutput;
import com.foten.goal.mapper.FinancialInfoMapper;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.StayInfoMapper;
import com.foten.goal.service.GoalCalculationService;
import com.foten.member.dto.OnboardingRequest;
import com.foten.member.dto.OnboardingResponse;
import com.foten.product.domain.SavingsRoadmapVO;
import com.foten.product.mapper.SavingsRoadmapMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    private static final long MEMBER_ID = 1L;

    @Mock
    private StayInfoMapper stayInfoMapper;
    @Mock
    private FinancialInfoMapper financialInfoMapper;
    @Mock
    private GoalMapper goalMapper;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private GoalCalculationService goalCalculationService;
    @Mock
    private SavingsRoadmapMapper savingsRoadmapMapper;

    private OnboardingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OnboardingServiceImpl(
                stayInfoMapper, financialInfoMapper, goalMapper, exchangeRateService, goalCalculationService,
                savingsRoadmapMapper);
    }

    private static OnboardingRequest 온보딩요청() {
        return new OnboardingRequest(
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusYears(2),
                BigDecimal.valueOf(2_500_000),
                BigDecimal.valueOf(300_000),
                BigDecimal.valueOf(800_000),
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(420_000_000),
                "VND");
    }

    @Test
    void register_로드맵이_없으면_정상_등록된다() {
        when(savingsRoadmapMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(exchangeRateService.toKrw(eq("VND"), any())).thenReturn(new KrwConversionResponse(
                "VND", BigDecimal.valueOf(420_000_000), BigDecimal.valueOf(21_000_000),
                BigDecimal.valueOf(0.05), LocalDate.now(), false));
        when(goalCalculationService.calculate(any()))
                .thenReturn(new GoalCalculationOutput(BigDecimal.valueOf(1_000_000), 20));

        OnboardingResponse response = service.register(MEMBER_ID, 온보딩요청());

        assertEquals("VND", response.targetCurrency());
        assertEquals(BigDecimal.valueOf(1_000_000), response.targetBaselineAmount());
        assertEquals(20, response.remainingMonths());
        verify(goalMapper).upsert(any());
    }

    @Test
    void register_목표에_target_amount_krw를_환산액으로_저장한다() {
        when(savingsRoadmapMapper.selectByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(exchangeRateService.toKrw(eq("VND"), any())).thenReturn(new KrwConversionResponse(
                "VND", BigDecimal.valueOf(420_000_000), BigDecimal.valueOf(21_000_000),
                BigDecimal.valueOf(0.05), LocalDate.now(), false));
        when(goalCalculationService.calculate(any()))
                .thenReturn(new GoalCalculationOutput(BigDecimal.valueOf(1_000_000), 20));

        service.register(MEMBER_ID, 온보딩요청());

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalMapper).upsert(captor.capture());
        assertEquals(BigDecimal.valueOf(21_000_000), captor.getValue().getTargetAmountKrw());
    }

    @Test
    void register_로드맵이_이미_있으면_RoadmapStateConflictException을_던진다() {
        when(savingsRoadmapMapper.selectByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(SavingsRoadmapVO.builder().build()));

        RoadmapStateConflictException exception = assertThrows(
                RoadmapStateConflictException.class, () -> service.register(MEMBER_ID, 온보딩요청()));

        assertEquals("ROADMAP_ALREADY_EXISTS", exception.getErrorCode());
        verifyNoInteractions(exchangeRateService, goalCalculationService);
        verify(stayInfoMapper, never()).upsert(any());
        verify(financialInfoMapper, never()).upsert(any());
        verify(goalMapper, never()).upsert(any());
    }
}
