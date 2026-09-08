import { defineStore } from 'pinia'
import { goalApi, memberApi, spendingApi, exchangeApi } from '../api'
import { nationalityOf } from '../i18n'

/*
 * 대시보드·소비내역·내 정보가 함께 쓰는 읽기 데이터.
 *
 *  me         GET /users/me            { member, residence, finance, goal }
 *  diagnosis  GET /goals/current/diagnosis
 *  spending   GET /spending?monthsAgo   { month, daysCovered, total, fixedTotal, variableTotal,
 *                                          fixedByCategory{}, variableByCategory{} }
 *  fx         GET /exchange-rates/{code}/krw?amount=목표금액  → { krwAmount, rate, baseDate, stale }
 *
 *  "지금까지 모은 돈"(actualCumulativeSavings)은 진단 API 가 아직 안 내려준다(#25).
 *  값이 오면 그대로 쓰도록 필드만 열어둔다 → savedAmount getter.
 */
export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    me: null,
    diagnosis: null,
    fx: null,
    spending: {}, // monthsAgo → 응답
    loading: false,
    error: '', // 서버가 내려준 메시지. 언어가 고정된 문자열이라 그대로 표시한다.
    loadFailed: false, // 서버 메시지가 없는 실패(네트워크 끊김 등) — 화면에서 t()로 안내문을 고른다.
  }),

  getters: {
    nationality: (s) => nationalityOf(s.me?.member?.nationality),
    currency() {
      return this.nationality?.currency || this.me?.goal?.targetCurrency || ''
    },
    symbol() {
      return this.nationality?.symbol || ''
    },
    targetKrw: (s) => s.fx?.krwAmount ?? null,
    rate: (s) => s.fx?.rate ?? null, // 1원 = rate 외화
    savedAmount: (s) => s.diagnosis?.actualCumulativeSavings ?? null, // TODO(#25)
    savedLocal() {
      return this.savedAmount !== null && this.rate ? Math.round(this.savedAmount * this.rate) : null
    },
    savingRoom: (s) =>
      s.diagnosis ? Number(s.diagnosis.maxExpectedSaving) - Number(s.diagnosis.currentExpectedSaving) : null,
  },

  actions: {
    async loadMe(force = false) {
      if (this.me && !force) return this.me
      this.me = await memberApi.me()
      return this.me
    },

    async loadDiagnosis(force = false) {
      if (this.diagnosis && !force) return this.diagnosis
      this.diagnosis = await goalApi.diagnosis()
      return this.diagnosis
    },

    async loadFx() {
      const goal = this.me?.goal
      if (!goal?.targetCurrency || !goal?.targetAmount) return null
      try {
        this.fx = await exchangeApi.toKrw(goal.targetCurrency, goal.targetAmount)
      } catch {
        this.fx = null // 환율이 아직 없는 통화면 404 — 원화 환산만 비운다
      }
      return this.fx
    },

    async loadSpending(monthsAgo = 0, force = false) {
      if (this.spending[monthsAgo] && !force) return this.spending[monthsAgo]
      const data = await spendingApi.monthly(monthsAgo)
      this.spending = { ...this.spending, [monthsAgo]: data }
      return data
    },

    // 대시보드 첫 진입: 셋을 병렬로. 하나가 실패해도 나머지는 그린다.
    async loadHome(force = false) {
      this.loading = true
      this.error = ''
      this.loadFailed = false
      const results = await Promise.allSettled([
        this.loadMe(force).then(() => this.loadFx()),
        this.loadDiagnosis(force),
        this.loadSpending(0, force),
      ])
      const failed = results.find((r) => r.status === 'rejected')
      if (failed) {
        // 응답 자체가 없는 실패(네트워크 끊김·타임아웃)는 response 가 없어 메시지도 없다. 여기서
        // t()로 안내문을 확정해 저장하면 화면 언어를 나중에 바꿔도 저장된 문자열은 안 바뀐다 —
        // 그래서 실패 사실만 loadFailed 로 남기고, 실제 문구는 화면(t() 호출)에서 매번 새로 고른다.
        this.error = failed.reason?.response?.data?.message || ''
        this.loadFailed = true
      }
      this.loading = false
    },

    // 마이페이지 진입: loadHome()과 달리 me만 있으면 되고, 실패해도 나머지를 그릴 게 없다.
    async loadMePage(force = false) {
      this.loading = true
      this.error = ''
      this.loadFailed = false
      try {
        await this.loadMe(force)
        await this.loadFx()
      } catch (err) {
        this.error = err?.response?.data?.message || ''
        this.loadFailed = true
      } finally {
        this.loading = false
      }
    },

    invalidate() {
      this.me = null
      this.diagnosis = null
      this.fx = null
      this.spending = {}
    },
  },
})
