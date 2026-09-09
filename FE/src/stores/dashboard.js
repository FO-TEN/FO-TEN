import { defineStore } from 'pinia'
import { goalApi, memberApi, spendingApi, exchangeApi, roadmapApi } from '../api'
import { setServerToday } from '../utils/clock'
import { nationalityOf } from '../i18n'
import { errorKey } from '../api/http'

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
    // 홈 저축 로드맵 (#121). status 는 로드맵이 없어도 200, graph 는 있을 때만 (챗봇 ROADMAP 카드와 같은 모양)
    roadmapStatus: null,
    roadmapGraph: null,
    loading: false,
    /*
     * 실패 안내는 문구가 아니라 i18n 키로 들고 있는다. 여기서 t()로 문구를 확정해 저장하면
     * 화면 언어를 나중에 바꿔도 저장된 문자열은 그대로라 한 화면만 옛 언어로 남는다.
     * 서버 message 를 담지 않는 이유는 또 있다 — 그건 한국어로 고정이라 19개 언어 화면에
     * 그대로 새어 나간다.
     */
    errorKey: '',
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
      setServerToday(this.me?.today)
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

    // 상태 → 있으면 그래프까지. 그래프만 실패하면 상태는 남긴다
    async loadRoadmap(force = false) {
      if (this.roadmapStatus && !force) return this.roadmapStatus
      this.roadmapStatus = await roadmapApi.status()
      if (this.roadmapStatus?.roadmapExists) {
        try {
          this.roadmapGraph = await roadmapApi.graph()
        } catch {
          this.roadmapGraph = null
        }
      } else {
        this.roadmapGraph = null
      }
      return this.roadmapStatus
    },

    // 대시보드 첫 진입: 병렬로. 하나가 실패해도 나머지는 그린다.
    async loadHome(force = false) {
      this.loading = true
      this.errorKey = ''
      const results = await Promise.allSettled([
        this.loadMe(force).then(() => this.loadFx()),
        this.loadDiagnosis(force),
        this.loadRoadmap(force),
      ])
      const failed = results.find((r) => r.status === 'rejected')
      if (failed) {
        this.errorKey = errorKey(failed.reason)
      }
      this.loading = false
    },

    // 마이페이지 진입: loadHome()과 달리 me만 있으면 되고, 실패해도 나머지를 그릴 게 없다.
    async loadMePage(force = false) {
      this.loading = true
      this.errorKey = ''
      try {
        await this.loadMe(force)
        await this.loadFx()
      } catch (err) {
        this.errorKey = errorKey(err)
      } finally {
        this.loading = false
      }
    },

    invalidate() {
      this.me = null
      this.diagnosis = null
      this.fx = null
      this.spending = {}
      this.roadmapStatus = null
      this.roadmapGraph = null
    },
  },
})
