package com.foten.member.service;

import com.foten.common.InvalidRequestException;
import com.foten.common.RoadmapStateConflictException;
import com.foten.exchange.dto.KrwConversionResponse;
import com.foten.exchange.service.ExchangeRateService;
import com.foten.goal.domain.*;
import com.foten.goal.mapper.FinancialInfoMapper;
import com.foten.goal.mapper.GoalMapper;
import com.foten.goal.mapper.StayInfoMapper;
import com.foten.goal.service.GoalCalculationService;
import com.foten.member.dto.OnboardingRequest;
import com.foten.member.dto.OnboardingResponse;
import com.foten.member.dto.OnboardingStatusResponse;
import com.foten.product.mapper.SavingsRoadmapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService{

    private static final int CURRENCY_CODE_LENGTH = 3;

    private final StayInfoMapper stayInfoMapper;
    private final FinancialInfoMapper financialInfoMapper;
    private final GoalMapper goalMapper;
    private final ExchangeRateService exchangeRateService;
    private final GoalCalculationService goalCalculationService;
    private final SavingsRoadmapMapper savingsRoadmapMapper;

    @Override
    @Transactional
    public OnboardingResponse register(long memberId, OnboardingRequest request) {
        validate(request);

        // 로드맵이 이미 시작된 회원은 목표기준액을 다시 바꿀 수 없다 — product 도메인이
        // 이 값을 스냅샷 없이 매번 goal 테이블에서 실시간으로 읽어가므로, 여기서 바뀌면
        // 이미 가입된 적금 상품 구성·로드맵 판정이 통째로 어긋난다 (기획서 §15).
        if (savingsRoadmapMapper.selectByMemberId(memberId).isPresent()) {
            throw new RoadmapStateConflictException(
                    "ROADMAP_ALREADY_EXISTS", "이미 로드맵이 시작된 목표는 수정할 수 없습니다. memberId=" + memberId);
        }

        String currencyCode = request.targetCurrency().trim().toUpperCase();

        // 아무도 목표를 세운 적 없는 통화면 환율이 없다. 여기서 한 번 받아둬야 아래 toKrw 가 산다.
        // 외부 호출이 트랜잭션 안에 들어오지만, 실제 호출은 그 통화의 최초 1회뿐이다.
        exchangeRateService.ensureRate(currencyCode);
        KrwConversionResponse converted =
                exchangeRateService.toKrw(currencyCode, request.targetAmount());

        GoalCalculationOutput calculated = goalCalculationService.calculate(new GoalCalculationInput(
                converted.krwAmount(),
                request.currentSavings(),
                LocalDate.now(),
                request.expectedReturnDate()
        ));

        stayInfoMapper.upsert(StayInfo.builder()
                .memberId(memberId)
                .entryDate(request.entryDate())
                .expectedReturnDate(request.expectedReturnDate())
                .build()
        );

        financialInfoMapper.upsert(FinancialInfo.builder()
                .memberId(memberId)
                .monthlyIncome(request.monthlyIncome())
                .monthlyLivingCost(request.monthlyLivingCost())
                .monthlyRemittance(request.monthlyRemittance())
                .currentSavings(request.currentSavings())
                .build()
        );

        goalMapper.upsert(Goal.builder()
                .memberId(memberId)
                .targetAmount(request.targetAmount())
                .targetCurrency(currencyCode)
                .targetBaselineAmount(calculated.targetBaselineAmount())
                .build()
        );

        return new OnboardingResponse(
                request.targetAmount(),
                currencyCode,
                converted.krwAmount(),
                calculated.targetBaselineAmount(),
                calculated.remainingMonths(),
                converted.rate(),
                converted.baseDate()
        );
    }

    @Override
    public OnboardingStatusResponse getStatus(long memberId) {
        return OnboardingStatusResponse.of(
                stayInfoMapper.selectByMemberId(memberId).isPresent(),
                financialInfoMapper.selectByMemberId(memberId).isPresent(),
                goalMapper.selectByMemberId(memberId).isPresent()
        );
    }

    private void validate(OnboardingRequest request) {
        LocalDate today = LocalDate.now();

        if (request.entryDate() == null || request.expectedReturnDate() == null) {
            throw new InvalidRequestException("입국일과 귀국 예정일이 모두 필요합니다.");
        }
        if (request.entryDate().isAfter(today)) {
            throw new InvalidRequestException("입국일은 오늘 이후일 수 없습니다.");
        }
        // 귀국일이 지났으면 남은 개월수가 최소 1개월로 보정돼 목표기준액이 목표금액 전액이 된다.
        if (!request.expectedReturnDate().isAfter(today)) {
            throw new InvalidRequestException("귀국 예정일은 오늘 이후여야 합니다.");
        }
        if (!request.entryDate().isBefore(request.expectedReturnDate())) {
            throw new InvalidRequestException("귀국 예정일은 입국일 이후여야 합니다.");
        }

        requireNotNegative(request.monthlyIncome(), "월 소득");
        requireNotNegative(request.monthlyLivingCost(), "월 고정비");
        requireNotNegative(request.monthlyRemittance(), "월 송금액");
        requireNotNegative(request.currentSavings(), "현재 자산");
        requirePositive(request.targetAmount(), "목표 금액");

        if (request.targetCurrency() == null || request.targetCurrency().isBlank()) {
            throw new InvalidRequestException("목표 통화 입력이 필요합니다.");
        }
        if (request.targetCurrency().trim().length() != CURRENCY_CODE_LENGTH) {
            throw new InvalidRequestException("목표 통화는 세 글자 코드여야 합니다. 예: VND");
        }
    }

    private void requireNotNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidRequestException(field + " 입력이 필요합니다.");
        }
        if (value.signum() < 0) {
            throw new InvalidRequestException(field + " 값이 음수일 수 없습니다.");
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException(field + " 값이 0보다 커야 합니다.");
        }
    }
}
