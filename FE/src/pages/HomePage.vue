<script setup>
import { computed, onMounted, onBeforeUnmount, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useDashboardStore } from '../stores/dashboard'
import { useChatStore } from '../stores/chat'
import { useLocaleStore } from '../stores/locale'
import { comma, dday, dotMonth, daysLeftInMonth, todayMonthDay } from '../utils/format'
import LangSwitch from '../components/ui/LangSwitch.vue'
import BottomNav from '../components/layout/BottomNav.vue'
import SavingBars from '../components/ui/SavingBars.vue'
import CategoryIcon from '../components/ui/CategoryIcon.vue'
import PotenAvatar from '../components/ui/PotenAvatar.vue'
import logo from '../assets/img/foten_logo.png'
import ellipse from '../assets/img/hero_ellipse.svg'
import chev from '../assets/icons/chevron_right.svg'

/*
 * Figma 04c_대시보드 v3(217:3047).
 *  히어로       GET /users/me + 환율 + 진단 achievementRate  ("모은 돈"은 #25 뒤 표시)
 *  이번 달 저축  진단 currentExpectedSaving / maxExpectedSaving / monthlyBaseline / additionalNeeded
 *  줄일 수 있는 곳  진단 topSavingCategory / topSavingAmount (1개)
 *  이번 달 소비  GET /spending?monthsAgo=0  ← "지난달 이맘때보다" 카드 대신 (비교는 안 하기로 함)
 *  CTA          대화 탭으로 이동 + 질문 자동 전송
 */
const router = useRouter()
const auth = useAuthStore()
const dash = useDashboardStore()
const chat = useChatStore()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

onMounted(() => dash.loadHome())

const me = computed(() => dash.me)
const dx = computed(() => dash.diagnosis)
const sp = computed(() => dash.spending[0])
const md = todayMonthDay()

// 히어로: 모은 돈이 오면 그걸, 아니면 목표 금액을 크게 (#25)
const heroAmount = computed(() => dash.savedAmount ?? dash.targetKrw)
const heroLocal = computed(() => (dash.savedAmount !== null ? dash.savedLocal : me.value?.goal?.targetAmount ?? null))
// 히어로 금액은 아바타 옆 좁은 칸에 들어가야 한다. 숫자가 길어지거나 단위가 "원" 이 아니라 "won"·"KRW" 로
// 바뀌면 넘치므로, 폭을 어림잡지 않고 실제로 그려진 크기를 재서 남는 만큼 글자를 줄인다.
const bigEl = ref(null)
const wonEl = ref(null)
const localEl = ref(null)

function fitOne(el, avail, base, min) {
  if (!el || avail <= 0) return
  el.style.fontSize = base + 'px'
  const natural = el.scrollWidth // .big·.local 은 flex-shrink 를 꺼 둬서 넘쳐도 원래 폭이 나온다
  const size = natural > avail ? Math.floor((base * avail) / natural) : base
  el.style.fontSize = Math.max(min, Math.min(base, size)) + 'px'
}

async function fitHero() {
  await nextTick()
  const row = bigEl.value?.parentElement
  if (row) fitOne(bigEl.value, row.clientWidth - (wonEl.value?.offsetWidth || 0) - 6, 30, 16)
  const col = localEl.value?.parentElement
  if (col) fitOne(localEl.value, col.clientWidth, 16, 11)
}

onMounted(() => {
  window.addEventListener('resize', fitHero)
  // 웹폰트가 늦게 오면 글자 폭이 달라진다. 폰트가 준비된 뒤 한 번 더 잰다.
  document.fonts?.ready.then(fitHero)
})
onBeforeUnmount(() => window.removeEventListener('resize', fitHero))
watch([heroAmount, heroLocal, () => locale.ui, () => dash.symbol], fitHero, { flush: 'post' })

const heroLabel = computed(() =>
  dash.savedAmount !== null ? t('home.saved_so_far', { name: auth.firstName }) : t('home.goal_amount_label', { name: auth.firstName }),
)
const rate = computed(() => Number(dx.value?.achievementRate ?? 0))
// additionalNeeded 는 "지금처럼 쓰면" 모자라는 돈이다. "줄여도 모자라요" 문장은 다 줄인 뒤의 차액이어야
// 하므로 목표(monthlyBaseline) − 최대 예상 저축(maxExpectedSaving) 을 직접 뺀다. 음수면 여유.
const gap = computed(() => (dx.value ? Number(dx.value.monthlyBaseline) - Number(dx.value.maxExpectedSaving) : null))

function goChat() {
  chat.queue(t('home.cta_question'))
  router.push({ name: 'chat' })
}
</script>

<template>
  <main class="home">
    <header class="hdr">
      <img :src="logo" alt="FO:TEN" class="logo" />
      <LangSwitch />
    </header>

    <div class="body">
      <!-- 히어로 -->
      <section class="hero">
        <div class="hero-top">
          <div class="hero-txt">
            <span class="pill num">{{ t('home.dday', { d: dday(me?.residence?.expectedReturnDate) }) }}</span>
            <p class="lbl">{{ heroLabel }}</p>
            <p class="amt"><span ref="bigEl" class="num big">{{ comma(heroAmount) }}</span><span ref="wonEl" class="won">{{ t('common.won') }}</span></p>
            <p class="lw"><span ref="localEl" class="local num">≈ {{ comma(heroLocal) }} {{ dash.symbol }}</span></p>
          </div>
          <div class="hero-img">
            <img :src="ellipse" alt="" class="ell" />
            <PotenAvatar :size="87" class="pot" />
          </div>
        </div>
        <div class="track"><div class="fill" :style="{ width: Math.min(100, rate) + '%' }" /></div>
        <div class="hero-foot">
          <span class="goal num">{{ t('home.goal_line', { amt: comma(dash.targetKrw), ym: dotMonth(me?.residence?.expectedReturnDate) }) }}</span>
          <span class="pct num">{{ rate }}%</span>
        </div>
      </section>

      <h2 class="sect">{{ t('home.how_this_month') }}</h2>

      <!-- 이번 달 저축 -->
      <section v-if="dx" class="card">
        <div class="row-between">
          <div class="col1">
            <p class="ct">{{ t('home.saving_this_month') }}</p>
            <p class="cs">{{ t('home.as_of', { m: md.month, d: md.day }) }}</p>
          </div>
          <p class="amt"><span class="num mid">{{ comma(dx.currentExpectedSaving) }}</span><span class="won16">{{ t('common.won') }}</span></p>
        </div>
        <p class="desc">{{ t('home.if_you_keep') }}</p>
        <SavingBars
          :current="dx.currentExpectedSaving"
          :saving="dx.maxExpectedSaving"
          :target="dx.monthlyBaseline"
          :label-current="t('home.bar_now')"
          :label-saving="t('home.bar_saving')"
          :label-target="t('home.bar_target')"
          :unit="t('common.won')"
        />
        <p v-if="gap > 0" class="warn">{{ t('home.short_even_if', { amt: comma(gap) }) }}</p>
        <p v-else class="ok">{{ t('home.surplus', { amt: comma(-gap) }) }}</p>
      </section>

      <!-- 줄일 수 있는 곳 -->
      <section v-if="dx && dx.topSavingCategory" class="card tight">
        <div class="row-between pb6">
          <div class="col1">
            <p class="ct">{{ t('home.where_to_cut') }}</p>
            <p class="cs">{{ t('home.days_left_month', { n: daysLeftInMonth() }) }}</p>
          </div>
          <p class="amt"><span class="num mid green">+{{ comma(dash.savingRoom) }}</span><span class="won16">{{ t('common.won') }}</span></p>
        </div>
        <div class="tip">
          <div class="tip-l">
            <CategoryIcon :category="dx.topSavingCategory" />
            <span class="tip-name">{{ t('cat.' + dx.topSavingCategory) }}</span>
          </div>
          <p class="amt"><span class="num semi">−{{ comma(dx.topSavingAmount) }}</span><span class="won14">{{ t('common.won') }}</span></p>
        </div>
      </section>

      <!-- 이번 달 소비 (지난달 비교 카드 대신) -->
      <section v-if="sp" class="card">
        <div class="row-between">
          <div class="col1">
            <p class="ct">{{ t('home.spending_this_month') }}</p>
            <p class="cs">{{ t('home.until', { m: md.month, d: sp.daysCovered }) }}</p>
          </div>
          <p class="amt"><span class="num mid">{{ comma(sp.total) }}</span><span class="won16">{{ t('common.won') }}</span></p>
        </div>
        <div class="sprow"><span class="dot fixed" /><span class="spl">{{ t('spending.fixed') }}</span><span class="num spv">{{ comma(sp.fixedTotal) }} {{ t('common.won') }}</span></div>
        <div class="sprow"><span class="dot var" /><span class="spl">{{ t('spending.variable') }}</span><span class="num spv">{{ comma(sp.variableTotal) }} {{ t('common.won') }}</span></div>
        <button type="button" class="link" @click="router.push({ name: 'spending' })">{{ t('home.see_all_spending') }} →</button>
      </section>

      <p v-if="dash.loading && !dx" class="err">{{ t('common.loading') }}</p>
      <p v-else-if="dash.error && !dx" class="err">{{ dash.error }}</p>
      <p v-else-if="dash.loadFailed && !dx" class="err">{{ t('common.load_failed') }}</p>

      <!-- CTA -->
      <button type="button" class="cta light" @click="router.push({ name: 'products' })">
        <span class="cta-l">
          <PotenAvatar :size="36" />
          <span class="cta-t">
            <span class="c1">{{ t('home.products_title') }}</span>
            <span class="c2">{{ t('home.products_sub') }}</span>
          </span>
        </span>
        <img :src="chev" alt="" width="16" height="16" />
      </button>

      <button type="button" class="cta" @click="goChat">
        <span class="cta-l">
          <PotenAvatar :size="36" />
          <span class="cta-t">
            <span class="c1">{{ t('home.cta_title') }}</span>
            <span class="c2">{{ t('home.cta_sub') }}</span>
          </span>
        </span>
        <img :src="chev" alt="" width="16" height="16" />
      </button>
    </div>

    <BottomNav />
  </main>
</template>

<style scoped>
.home {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--dash-bg);
}
.hdr {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-h);
  padding: 0 16px 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--border-soft);
}
.logo {
  width: 93px;
  height: 24px;
  object-fit: contain;
}
.body {
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px var(--page-x) 24px;
}

/* 히어로 */
.hero {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 22px;
  border-radius: var(--r-card-lg);
  background: var(--yellow-100);
  box-shadow: var(--shadow-hero);
}
.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hero-txt {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  overflow: hidden; /* 글자를 줄여도 넘치면 아바타를 덮지 않고 잘린다 */
}
.pill {
  align-self: flex-start;
  padding: 3px 10px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  font-weight: 700;
  color: var(--gray-850);
  line-height: 1.4;
}
.lbl {
  /* 16px 이면 "Nguyen Van A 님의 목표 금액" 처럼 이름이 길 때 두 줄이 된다. 텍스트 칸이 194px 뿐이다. */
  font-size: 14px;
  color: var(--gray-600);
  line-height: 1.4;
}
.amt {
  display: flex;
  align-items: baseline;
  gap: 3px;
  white-space: nowrap;
}
.big {
  flex: 0 0 auto; /* 줄어들면 자연 폭을 못 재서 fitHero 가 틀린다 */
  font-size: 30px;
  font-weight: 700;
  line-height: 34px;
  letter-spacing: -0.3px;
  color: var(--gray-850);
}
.won {
  flex: 0 0 auto;
  font-size: 17px;
  color: var(--gray-700);
}
.lw {
  min-width: 0;
}
.local {
  display: inline-block;
  white-space: nowrap;
  font-size: 16px;
  color: #78706b;
  line-height: 1.4;
}
.hero-img {
  position: relative;
  flex: 0 0 auto;
  width: 97px;
  height: 101px;
}
.ell {
  position: absolute;
  left: -3px;
  top: 2.5px;
  width: 98px;
  height: 98px;
}
.pot {
  position: absolute;
  left: 2px;
  top: 18px;
  width: 87px !important;
  height: 66px !important;
}
.track {
  height: 10px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.8);
  overflow: hidden;
}
.fill {
  height: 100%;
  background: var(--surface-primary);
  transition: width 0.5s ease;
}
.hero-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  line-height: 1.4;
}
.goal {
  font-size: 14px;
  color: var(--gray-600);
}
.pct {
  font-size: 16px;
  font-weight: 700;
  color: var(--gray-850);
}

.sect {
  padding-top: 14px;
  font-size: 22px;
  font-weight: 700;
  color: var(--gray-850);
  line-height: 1.4;
}

/* 카드 공통 */
.card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border-radius: var(--r-card-lg);
  background: #fff;
}
.card.tight {
  gap: 0;
}
.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.pb6 {
  padding-bottom: 6px;
}
.col1 {
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 1.4;
}
.ct {
  font-size: 18px;
  font-weight: 700;
  color: var(--gray-850);
}
.cs {
  font-size: 14px;
  color: #a8a39e;
}
.mid {
  font-size: 24px;
  font-weight: 700;
  line-height: 28px;
  letter-spacing: -0.12px;
  color: var(--gray-850);
}
.mid.green {
  color: var(--green);
}
.won16 {
  font-size: 16px;
  color: var(--gray-700);
}
.won14 {
  font-size: 14px;
  color: var(--gray-700);
}
.desc {
  font-size: 16px;
  color: var(--gray-700);
  line-height: 1.4;
}
.warn {
  font-size: 14px;
  font-weight: 500;
  color: var(--orange);
}
.ok {
  font-size: 14px;
  font-weight: 500;
  color: var(--green);
}
.tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}
.tip-l {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tip-name {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-850);
}
.semi {
  font-size: 18px;
  font-weight: 600;
  line-height: 22px;
  color: var(--gray-850);
}

.sprow {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 15px;
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}
.dot.fixed {
  background: var(--gray-700);
}
.dot.var {
  background: var(--surface-primary);
}
.spl {
  flex: 1 0 0;
  color: var(--gray-700);
}
.spv {
  font-weight: 600;
  color: var(--gray-850);
}
.link {
  align-self: flex-start;
  font-size: 14px;
  font-weight: 500;
  color: var(--yellow-link);
}
.err {
  font-size: 14px;
  color: var(--gray-500);
  text-align: center;
  padding: 12px;
}

/* CTA */
.cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 14px 16px;
  border-radius: var(--r-cta);
  background: var(--yellow-100);
  text-align: left;
}
/* 추천 조합 행은 흰 카드로, 대화 행(노랑)과 구분한다 */
.cta.light {
  background: #fff;
  border: 1px solid var(--border-soft);
}
.cta-l {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cta-t {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.c1 {
  font-size: 16px;
  font-weight: 700;
  color: var(--gray-850);
}
.c2 {
  font-size: 14px;
  color: var(--gray-500);
}
</style>
