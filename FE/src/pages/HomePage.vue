<script setup>
import { computed, onMounted, onBeforeUnmount, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useDashboardStore } from '../stores/dashboard'
import { useChatStore } from '../stores/chat'
import { useLocaleStore } from '../stores/locale'
import { comma, dday, dotMonth, ym } from '../utils/format'
import { today as clockToday, serverToday } from '../utils/clock'
import LangSwitch from '../components/ui/LangSwitch.vue'
import BottomNav from '../components/layout/BottomNav.vue'
import PotenAvatar from '../components/ui/PotenAvatar.vue'
import RoadmapGraphCard from '../components/ui/RoadmapGraphCard.vue'
import RoadmapEmptyCard from '../components/ui/RoadmapEmptyCard.vue'
import logo from '../assets/img/foten_logo.png'
import ellipse from '../assets/img/hero_ellipse.svg'

// Figma 04d_홈 · 로드맵 / 04d-0_홈 · 로드맵 생성 전 (#121)
// 저축 로드맵은 /roadmap/status 의 roadmapExists 로 갈린다. 없으면 생성 카드, 있으면 그래프 카드
const router = useRouter()
const auth = useAuthStore()
const dash = useDashboardStore()
const chat = useChatStore()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

onMounted(() => dash.loadHome())

const me = computed(() => dash.me)
const dx = computed(() => dash.diagnosis)

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

// 상태를 읽기 전에는 카드를 그리지 않는다. 생성 카드가 잠깐 떴다가 그래프로 바뀌면 어색하다
const roadmapLoaded = computed(() => dash.roadmapStatus !== null)
const hasRoadmap = computed(() => dash.roadmapStatus?.roadmapExists === true)
const graph = computed(() => dash.roadmapGraph)

// 제목은 보는 달, 부제는 로드맵이 끝나는 달(예상 귀국일 − 1개월) · 총 개월 · 구간 수
// 서버 "오늘"(시연 계정은 미래 날짜)을 따른다. serverToday 를 읽어 값이 바뀌면 다시 계산된다.
const today = computed(() => (serverToday.value, clockToday()))
// 제목의 달 = 가장 최근에 확정한 회차의 달(그래프 API latestPlanMonth). 이번 달 계획을 아직 확정하지 않았으면
// 지난달 로드맵으로 보이고, 확정하면 이번 달로 바뀐다. 값이 없으면(온보딩 직후) 오늘의 달.
const roadmapTitle = computed(() => {
  const latest = graph.value?.latestPlanMonth ? ym(graph.value.latestPlanMonth) : null
  const y = latest?.year ?? today.value.getFullYear()
  const m = latest?.month ?? today.value.getMonth() + 1
  return t('home.roadmap_title', { y, m })
})
const roadmapEnd = computed(() => {
  const iso = me.value?.residence?.expectedReturnDate
  if (!iso) return null
  const d = new Date(iso + 'T00:00:00')
  d.setMonth(d.getMonth() - 1)
  return { y: d.getFullYear(), m: d.getMonth() + 1 }
})
const roadmapSub = computed(() =>
  t('home.roadmap_sub', {
    y: roadmapEnd.value?.y ?? '—',
    m: roadmapEnd.value?.m ?? '—',
    n: graph.value?.totalMonths ?? 0,
    k: graph.value?.segments?.length ?? 0,
  }),
)

// 생성은 대화가 맡는다. 소개 턴 뒤 "내 로드맵 만들기" 칩을 눌러야 서버 흐름이 시작된다
function goCreate() {
  chat.queueIntro('roadmap')
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

      <h2 class="sect">{{ t('home.roadmap_section') }}</h2>

      <!-- 저축 로드맵: 없으면 생성 카드, 있으면 그래프 -->
      <template v-if="roadmapLoaded">
        <RoadmapEmptyCard v-if="!hasRoadmap" @create="goCreate" />
        <RoadmapGraphCard
          v-else-if="graph"
          :payload="graph"
          :title="roadmapTitle"
          :subtitle="roadmapSub"
          :show-note="false"
          class="graph"
        />
        <p v-else class="err">{{ t('home.roadmap_unavailable') }}</p>
      </template>
      <p v-else-if="dash.loading" class="err">{{ t('common.loading') }}</p>
      <p v-else-if="dash.errorKey" class="err">{{ t(dash.errorKey) }}</p>
    </div>

    <BottomNav />
  </main>
</template>

<style scoped>
.home {
  height: 100dvh; /* 화면 높이에 고정하고 .body 만 안에서 스크롤한다 — 헤더·하단바는 화면 밖으로 밀려나지 않는다 */
  display: flex;
  flex-direction: column;
  background: var(--dash-bg);
}
.hdr {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-h);
  padding: 0 16px 0 20px;
  background: var(--surface-card);
  border-bottom: 1px solid var(--border-soft);
}
.logo {
  width: 93px;
  height: 24px;
  object-fit: contain;
}
.body {
  flex: 1 1 0;
  min-height: 0; /* flex 항목 기본 min-height:auto 면 내용만큼 늘어나 overflow-y 가 안 먹는다 */
  overflow-y: auto;
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
  color: var(--gray-500);
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
  border-radius: var(--r-pill);
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

/* 대화 카드 기본값을 홈 카드 규격으로 */
.graph {
  margin-top: 0;
  padding: 20px 20px 18px;
  border-radius: var(--r-card-lg);
}

.err {
  font-size: 14px;
  color: var(--gray-500);
  text-align: center;
  padding: 12px;
}
</style>
