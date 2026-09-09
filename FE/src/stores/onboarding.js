import { defineStore } from 'pinia'
import { onboardingApi, exchangeApi } from '../api'
import { nationalityOf } from '../i18n'
import { useAuthStore } from './auth'
import { todayIso } from '../utils/clock'

/*
 * 온보딩 3화면(체류 → 재무 → 목표)의 입력을 여기 들고 가다가
 * 마지막 "시작하기"에서 POST /users/me/onboarding 을 한 번 부른다.
 * 백엔드가 세 테이블을 한 트랜잭션으로 쓰기 때문에 화면도 한 번에 보낸다.
 * "수정"(내 정보) 도 같은 화면·같은 API 다 — 다시 보내면 덮어쓴다.
 *
 * 금액은 원 또는 본국 통화로 입력할 수 있다. 2단계 네 칸은 칸마다 단위가 따로 있고(financeUnits), 3단계 목표는
 * targetUnit 이다. 백엔드는 재무 네 값은 원, 목표는 본국 통화로 받으므로 제출할 때 각각 맞춰 바꾼다.
 * 환율은 GET /exchange-rates/{code} 의 rate(1원당 외화). 단위를 바꾸면 이미 적은 값도 같은 돈이 되게 바꿔 준다.
 */
const FINANCE_KEYS = ['monthlyIncome', 'currentSavings', 'monthlyRemittance', 'monthlyLivingCost']

export const useOnboardingStore = defineStore('onboarding', {
  state: () => ({
    entryDate: '',
    expectedReturnDate: '',
    monthlyIncome: '',
    currentSavings: '',
    monthlyRemittance: '',
    monthlyLivingCost: '',
    // 2단계 칸별 입력 단위. 'KRW' | 본국 통화 코드. 송금은 본국 통화로, 급여는 원으로 적는 게 자연스러워 칸마다 따로 둔다.
    financeUnits: { monthlyIncome: 'KRW', currentSavings: 'KRW', monthlyRemittance: 'KRW', monthlyLivingCost: 'KRW' },
    targetAmount: '',
    targetUnit: 'KRW', // 'KRW' | 본국 통화 코드
    rate: null, // 1원 = rate 외화
    rateDate: '',
    result: null, // 등록 응답 { targetAmountKrw, targetBaselineAmount, remainingMonths, ... }
  }),

  getters: {
    // 통화는 가입 때 고른 국적으로 정한다. 백엔드는 targetCurrency(3글자)를 받는다.
    currency() {
      const auth = useAuthStore()
      const n = nationalityOf(auth.member?.nationality)
      return n ? { code: n.currency, symbol: n.symbol } : { code: 'USD', symbol: '$' }
    },
    // 본국 통화 금액 (백엔드에 보내는 값)
    targetAmountForeign: (s) => {
      if (s.targetAmount === '' || s.targetAmount === null) return null
      const v = Number(s.targetAmount)
      if (s.targetUnit !== 'KRW') return v
      return s.rate ? Math.round(v * s.rate) : null
    },
    // 원화 금액 (화면 표시용). 백엔드 toKrw 와 같은 식: 외화 ÷ rate, 반올림.
    targetAmountKrw: (s) => {
      if (s.targetAmount === '' || s.targetAmount === null) return null
      const v = Number(s.targetAmount)
      if (s.targetUnit === 'KRW') return v
      return s.rate ? Math.round(v / s.rate) : null
    },
    // 2단계 네 값을 원으로 (백엔드에 보내는 값). 본국 통화로 적은 칸은 ÷ rate.
    financeKrw() {
      const out = {}
      for (const k of FINANCE_KEYS) {
        const v = this[k]
        out[k] = v === '' || v === null ? null : this.financeUnits[k] === 'KRW' ? Number(v) : Math.round(Number(v) / this.rate)
      }
      return out
    },
    step1Valid: (s) =>
      !!s.entryDate && !!s.expectedReturnDate && s.entryDate < s.expectedReturnDate && s.expectedReturnDate > todayIso(),
    step2Valid: (s) =>
      FINANCE_KEYS.every((k) => (s.financeUnits[k] === 'KRW' || !!s.rate) && s[k] !== '' && Number(s[k]) >= 0),
    step3Valid() {
      return this.targetAmountForeign !== null && this.targetAmountForeign > 0
    },
  },

  actions: {
    // 2단계 진입 때 한 번. 환율이 없는 통화면 2단계는 원으로만, 3단계는 본국 통화로만 받는다.
    async loadRate() {
      try {
        const r = await exchangeApi.latest(this.currency.code)
        this.rate = Number(r.rate)
        this.rateDate = r.baseDate || ''
      } catch {
        this.rate = null
        this.rateDate = ''
        for (const k of FINANCE_KEYS) this.financeUnits[k] = 'KRW'
        this.targetUnit = this.currency.code
      }
      return this.rate
    },

    // 단위를 바꾸면 적어 둔 값도 같은 돈이 되게 바꾼다. 3,000,000원 → ₱ 로 바꾸면 139,380 이 보인다.
    setUnit(unit) {
      if (unit === this.targetUnit) return
      this.targetAmount = this.convert(this.targetAmount, this.targetUnit, unit)
      this.targetUnit = unit
    },
    setFinanceUnit(key, unit) {
      if (unit === this.financeUnits[key] || !this.rate) return
      this[key] = this.convert(this[key], this.financeUnits[key], unit)
      this.financeUnits[key] = unit
    },
    convert(v, from, to) {
      if (v === '' || v === null || !this.rate || from === to) return v
      return to === 'KRW' ? Math.round(Number(v) / this.rate) : Math.round(Number(v) * this.rate)
    },

    // 내 정보 "수정" 으로 들어올 때 현재 값으로 채운다. 저장된 목표는 본국 통화다.
    prefill({ residence, finance, goal }) {
      if (residence) {
        this.entryDate = residence.entryDate || ''
        this.expectedReturnDate = residence.expectedReturnDate || ''
      }
      if (finance) {
        for (const k of FINANCE_KEYS) this.financeUnits[k] = 'KRW' // 저장된 재무 값은 원이다
        this.monthlyIncome = finance.monthlyIncome ?? ''
        this.currentSavings = finance.currentSavings ?? ''
        this.monthlyRemittance = finance.monthlyRemittance ?? ''
        this.monthlyLivingCost = finance.monthlyLivingCost ?? ''
      }
      if (goal) {
        this.targetAmount = goal.targetAmount ?? ''
        this.targetUnit = goal.targetCurrency || this.currency.code
      }
    },

    async submit() {
      const body = {
        entryDate: this.entryDate,
        expectedReturnDate: this.expectedReturnDate,
        ...this.financeKrw,
        targetAmount: this.targetAmountForeign,
        targetCurrency: this.currency.code,
      }
      this.result = await onboardingApi.register(body)
      await useAuthStore().refreshOnboarding()
      return this.result
    },

    reset() {
      this.$reset()
    },
  },
})

