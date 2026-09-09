<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { useLocaleStore } from '../stores/locale'
import { roadmapApi } from '../api'
import { comma } from '../utils/format'
import { PRODUCT_PALETTE } from '../utils/productColors'
import AppHeader from '../components/layout/AppHeader.vue'
import LangSwitch from '../components/ui/LangSwitch.vue'
import BottomNav from '../components/layout/BottomNav.vue'
import hero from '../assets/img/poten_hero.png'
import surprised from '../assets/img/poten_surprised.png'
import iconWallet from '../assets/icons/product_wallet.svg'
import iconBank from '../assets/icons/product_bank.svg'
import iconPercent from '../assets/icons/product_percent.svg'
import iconCoins from '../assets/icons/product_coins.svg'

/*
 * Figma 10i_상품 추천 상세.
 *  탭·슬라이드  상품 구성 API(4-5) — 목돈(예금)과 매달 모을 돈(적금·현금성)
 *  값은 전부 서버 계산. 화면에서는 비율만 만든다.
 */
const router = useRouter()
const chat = useChatStore()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const loading = ref(true)
const composition = ref(null)
// 4-5 실패 원인 구분용. 404(로드맵 없음)·409(상품 미선택)는 "아직 안 정한" 정상 상태라
// 기존 안내를 그대로 두고, 응답이 없거나(네트워크 실패) 5xx면 진짜 서버 오류로 본다 —
// 이땐 "포텐이와 상품 정하기"로 유도하면 안 된다(서버가 고장난 거라 챗봇 가도 안 풀림).
const productsError = ref(false)

async function loadProducts() {
  try {
    composition.value = await roadmapApi.composition()
    productsError.value = false
  } catch (e) {
    composition.value = null
    const status = e?.response?.status
    productsError.value = !status || status >= 500
  }
}

onMounted(async () => {
  await loadProducts()
  loading.value = false
})

const n = (v) => (v === null || v === undefined ? 0 : Number(v))
const has = (v) => v !== null && v !== undefined && Number(v) > 0

// ── 슬라이드 ────────────────────────────────────────────
// 각 슬라이드는 { key, title, total, rows[] }. rows 는 { name, sub, amount, icon, bg, color }.
const lump = computed(() => {
  const c = composition.value
  if (!c || !c.deposit) return null
  const d = c.deposit
  const total = has(c.rolloverAmount) ? n(c.rolloverAmount) : n(d.principal)
  const rows = [
    {
      name: d.productName,
      sub: `${t('card.months', { n: d.termMonths })} · ${d.appliedRate}%`,
      amount: n(d.principal),
      icon: iconBank,
      bg: '#769fcd',
    },
  ]
  const rest = total - n(d.principal)
  if (rest > 0) {
    rows.unshift({ name: t('products.cash_name'), sub: t('products.cash_sub'), amount: rest, icon: iconWallet, bg: '#d6e6f2' })
  }
  return { key: 'lump', title: t('products.lump'), total, rows: withShares(rows, total, PRODUCT_PALETTE.lump) }
})

const monthly = computed(() => {
  const c = composition.value
  if (!c) return null
  const total = n(c.monthlyBaseline)
  const rows = (c.savings || []).map((s, i) => ({
    name: s.productName,
    sub: `${t('card.months', { n: s.termMonths })} · ${s.appliedRate}%`,
    amount: n(s.monthlyAllocated),
    icon: i === 0 ? iconPercent : iconCoins,
    bg: i === 0 ? '#ff9494' : '#ffd1d1',
  }))
  if (has(c.recommendedCashSaving)) {
    rows.push({ name: t('products.cash_name'), sub: t('products.cash_sub'), amount: n(c.recommendedCashSaving), icon: iconWallet, bg: '#d6e6f2' })
  }
  return { key: 'monthly', title: t('products.monthly'), total, rows: withShares(rows, total, PRODUCT_PALETTE.monthly) }
})

function withShares(rows, total, palette) {
  return rows.map((r, i) => ({
    ...r,
    color: palette[i % palette.length],
    share: total > 0 ? Math.round((r.amount / total) * 1000) / 10 : 0,
  }))
}

const slides = computed(() => [monthly.value, lump.value].filter(Boolean))
const active = ref(0)
const GAP = 12
const track = ref(null)

// 한 칸 = 카드 폭 + 간격
function step() {
  const el = track.value
  const first = el?.firstElementChild
  return first ? first.offsetWidth + GAP : 1
}
function goTo(i) {
  active.value = i
  const el = track.value
  if (!el) return
  // 부드럽게 움직이면 화면이 가려진 상태에서 멈춰 버린다. 스냅이 있어 바로 옮겨도 자연스럽다.
  el.scrollTo({ left: step() * i })
}
function onScroll() {
  const el = track.value
  if (!el) return
  active.value = Math.round(el.scrollLeft / step())
}

// 도넛: 원 둘레를 비율만큼 잘라 이어 붙인다. 두껍게 그리려고 r을 줄이고 두께를 키웠다.
const R = 34
const STROKE = 26
const CIRC = 2 * Math.PI * R
function arcs(rows) {
  let offset = 0
  return rows.map((r) => {
    const len = (CIRC * r.share) / 100
    const a = { color: r.color, dash: `${len} ${CIRC - len}`, offset: -offset }
    offset += len
    return a
  })
}
// 각 조각 가운데 각도에 퍼센트 라벨을 얹는다. -90도 회전(12시 시작) 기준은 arcs()와 맞춘다.
function arcLabels(rows) {
  let offset = 0
  const out = []
  for (const r of rows) {
    const len = (CIRC * r.share) / 100
    if (r.share > 0) {
      const angle = ((offset + len / 2) / CIRC) * 2 * Math.PI - Math.PI / 2
      out.push({ x: 50 + R * Math.cos(angle), y: 50 + R * Math.sin(angle), share: r.share })
    }
    offset += len
  }
  return out
}

function toChat(question) {
  chat.pendingQuestion = question
  router.push({ name: 'chat' })
}
</script>

<template>
  <div class="page">
    <AppHeader back :title="t('products.title')">
      <template #right><LangSwitch /></template>
    </AppHeader>

    <main class="body">
      <p v-if="loading" class="hint">{{ t('common.loading') }}</p>

      <template v-else-if="!composition">
        <section class="empty">
          <img :src="productsError ? surprised : hero" alt="" class="empty-img" />
          <template v-if="productsError">
            <p class="empty-t">{{ t('products.error_title') }}</p>
            <p class="empty-s">{{ t('products.error_sub') }}</p>
            <button type="button" class="cta" @click="loadProducts">{{ t('products.error_retry') }}</button>
          </template>
          <template v-else>
            <p class="empty-t">{{ t('products.empty_title') }}</p>
            <p class="empty-s">{{ t('products.empty_sub') }}</p>
            <button type="button" class="cta" @click="toChat(t('products.empty_question'))">{{ t('products.empty_cta') }}</button>
          </template>
        </section>
      </template>

      <template v-else>
        <h2 class="sect">{{ t('products.section') }}</h2>

        <div class="tabs">
          <button
            v-for="(s, i) in slides"
            :key="s.key"
            type="button"
            class="tab"
            :class="{ on: active === i }"
            @click="goTo(i)"
          >
            {{ s.key === 'lump' ? t('products.tab_lump') : t('products.tab_monthly') }}
          </button>
        </div>

        <div class="carousel">
        <button v-if="slides.length > 1 && active > 0" type="button" class="arrow left" :aria-label="t('products.prev')" @click="goTo(active - 1)">&lsaquo;</button>
        <button v-if="slides.length > 1 && active < slides.length - 1" type="button" class="arrow right" :aria-label="t('products.next')" @click="goTo(active + 1)">&rsaquo;</button>
        <div ref="track" class="track" @scroll.passive="onScroll">
          <section v-for="s in slides" :key="s.key" class="slide">
            <div class="s-head">
              <p class="st">{{ s.title }}</p>
              <p class="sv"><b class="num">{{ comma(s.total) }}</b><span class="won">{{ t('common.won') }}</span></p>
            </div>

            <div class="donut-row">
              <svg class="donut" viewBox="0 0 100 100" aria-hidden="true">
                <circle cx="50" cy="50" :r="R" fill="none" stroke="var(--gray-100)" :stroke-width="STROKE" />
                <circle
                  v-for="(a, i) in arcs(s.rows)"
                  :key="i"
                  cx="50"
                  cy="50"
                  :r="R"
                  fill="none"
                  :stroke="a.color"
                  :stroke-width="STROKE"
                  :stroke-dasharray="a.dash"
                  :stroke-dashoffset="a.offset"
                  transform="rotate(-90 50 50)"
                />
                <text
                  v-for="(l, i) in arcLabels(s.rows)"
                  :key="'pct' + i"
                  :x="l.x"
                  :y="l.y"
                  class="donut-pct"
                  text-anchor="middle"
                  dominant-baseline="middle"
                >{{ l.share }}%</text>
              </svg>
            </div>

            <div class="s-line" />

            <div v-for="r in s.rows" :key="'row-' + r.name" class="prow">
              <div class="p-left">
                <span class="p-ic" :style="{ background: r.bg }"><img :src="r.icon" alt="" width="20" height="20" /></span>
                <div class="p-txt">
                  <span class="pn">{{ r.name }}</span>
                  <span class="ps">{{ r.sub }}</span>
                </div>
              </div>
              <div class="p-right">
                <span class="pa"><b class="num">{{ comma(r.amount) }}</b><span class="won-s">{{ t('common.won') }}</span></span>
              </div>
            </div>
          </section>
        </div>
        </div>

        <div v-if="slides.length > 1" class="dots">
          <button v-for="(s, i) in slides" :key="s.key" type="button" class="d" :class="{ on: active === i }" :aria-label="s.title" @click="goTo(i)" />
        </div>
      </template>
    </main>

    <BottomNav />
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100dvh; /* 화면 높이에 고정하고 .body 만 안에서 스크롤한다 — 헤더·하단바는 화면 밖으로 밀려나지 않는다 */
  background: var(--dash-bg);
}
.body {
  flex: 1 1 0;
  min-height: 0; /* flex 항목 기본 min-height:auto 면 내용만큼 늘어나 overflow-y 가 안 먹는다 */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px var(--page-x) 24px; /* 하단바가 더는 .body 위에 겹쳐 뜨지 않아 nav 높이만큼 더 줄 필요가 없다 */
}
.hint {
  padding: 40px 0;
  text-align: center;
  color: var(--gray-500);
}

.sect {
  padding-top: 14px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}

/* 탭 */
.tabs {
  display: flex;
  gap: 8px;
  padding-bottom: 2px;
}
.tab {
  padding: 4px 12px;
  border-radius: var(--r-pill);
  border: 1px solid var(--gray-300);
  background: var(--surface-card);
  font-size: 16px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-700);
}
.tab.on {
  border-color: var(--gray-850);
  background: var(--gray-850);
  color: #fff;
  font-weight: 700;
}

/* 슬라이드: 카드 하나가 꽉 찬다. 밀어서 넘기거나 양옆 화살표·점으로 넘긴다 */
.carousel {
  position: relative;
}
.track {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
}
.track::-webkit-scrollbar {
  display: none;
}
.slide {
  flex: 0 0 100%;
  min-width: 0; /* nowrap 글자가 카드를 늘리지 못하게 */
  scroll-snap-align: start;
  display: flex;
  flex-direction: column;
  padding: 24px 18px;
  border-radius: var(--r-card-lg);
  background: var(--surface-card);
  border: 1px solid var(--gray-200);
}
/* 화살표: 마우스를 올리면 나타난다. 터치에서는 밀어서 넘긴다 */
.arrow {
  position: absolute;
  top: 50%;
  z-index: 1;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(38, 36, 31, 0.72);
  color: #fff;
  font-size: 22px;
  line-height: 32px;
  text-align: center;
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity 0.15s;
}
.arrow.left {
  left: 6px;
}
.arrow.right {
  right: 6px;
}
.carousel:hover .arrow,
.arrow:focus-visible {
  opacity: 1;
}
.s-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
}
.st {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}
.sv {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}
.sv b {
  font-size: 18px;
  font-weight: 600;
  line-height: 22px;
  color: var(--gray-850);
}
.won {
  font-size: 13px;
  color: var(--gray-700);
}

.donut-row {
  display: flex;
  justify-content: center;
  padding: 12px 0 16px;
}
.donut {
  width: 220px;
  height: 220px;
}
.donut-pct {
  font-size: 8.5px;
  font-weight: 700;
  fill: #fff;
  paint-order: stroke;
  stroke: rgba(0, 0, 0, 0.28);
  stroke-width: 3px;
  stroke-linejoin: round;
}
.s-line {
  height: 1px;
  background: var(--switch-bg);
}

.prow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 0;
}
.p-left {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}
.p-ic {
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}
.p-ic img {
  width: 20px;
  height: 20px;
}
.p-txt {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.pn {
  font-size: 15px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-850);
  overflow-wrap: anywhere; /* 상품명이 길면 줄을 바꾼다 */
}
.ps {
  font-size: 13px;
  line-height: 1.4;
  color: var(--gray-400);
}
.p-right {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.pa {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}
.pa b {
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: var(--gray-850);
}
.won-s {
  font-size: 11px;
  color: var(--gray-700);
}

.dots {
  display: flex;
  justify-content: center;
  gap: 6px;
}
.d {
  padding: 0;
  width: 6px;
  height: 6px;
  border-radius: 3px;
  background: var(--gray-300);
  transition: width 0.2s;
}
.d.on {
  width: 18px;
  background: var(--gray-850);
}

/* 아직 상품이 없을 때 */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 20px 24px;
  text-align: center;
}
.empty-img {
  width: 93px;
  height: 74px;
  object-fit: contain;
  margin-bottom: 8px;
}
.empty-t {
  font-size: 18px;
  font-weight: 700;
  color: var(--gray-850);
}
.empty-s {
  font-size: 14px;
  color: var(--gray-600);
}
.cta {
  margin-top: 12px;
  padding: 12px 20px;
  border-radius: var(--r-cta);
  background: var(--surface-primary);
  color: var(--on-primary);
  font-size: 15px;
  font-weight: 600;
}
</style>
