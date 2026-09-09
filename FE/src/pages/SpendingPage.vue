<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useDashboardStore } from '../stores/dashboard'
import { useLocaleStore } from '../stores/locale'
import { won, ym } from '../utils/format'
import { today as clockToday, serverToday } from '../utils/clock'
import { errorKey } from '../api/http'
import AppHeader from '../components/layout/AppHeader.vue'
import LangSwitch from '../components/ui/LangSwitch.vue'
import BottomNav from '../components/layout/BottomNav.vue'
import SpendingDonut from '../components/ui/SpendingDonut.vue'
import SpendingLegend from '../components/ui/SpendingLegend.vue'

/*
 * Figma 09_소비 내역(217:1874).
 *  GET /spending?monthsAgo=N → { month:'2026-09', daysCovered, total, fixedTotal, variableTotal,
 *                                fixedByCategory{}, variableByCategory{}, categoryTotals:[{category,amount}] }
 *  고정비/변동비 구분 표시는 화면에서 뺐다(카테고리별 도넛+전체 목록으로 대체) — fixedTotal/variableTotal 등은
 *  챗봇(SpendingTools)이 여전히 쓰므로 API 응답에는 남아있고, 이 화면은 categoryTotals만 쓴다.
 *  홈 화면은 categoryTotals 중 앞 3개만 잘라 쓰고, 이 화면은 전체를 보여준다.
 *  "지난달 같은 날" 비교는 안 하기로 해서 뺐다. 이번 달일 때만 저축 예상 카드(진단 API)를 붙인다.
 *  헤더에 LangSwitch 추가 (규칙 10). 월 선택은 헤더 아래 monthbar 에 둔다.
 */
const dash = useDashboardStore()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const monthsAgo = ref(0)
const open = ref(false)
const loading = ref(false)
const error = ref('')

const data = computed(() => dash.spending[monthsAgo.value])
const dx = computed(() => (monthsAgo.value === 0 ? dash.diagnosis : null))

// 최근 6개월 선택지 (시드가 6개월치라 그 범위)
// 서버 "오늘" 기준(시연 계정은 미래 날짜). serverToday 가 바뀌면 라벨도 다시 계산된다.
const months = computed(() =>
  Array.from({ length: 6 }, (_, i) => {
    void serverToday.value
    const d = clockToday()
    d.setDate(1)
    d.setMonth(d.getMonth() - i)
    return { monthsAgo: i, label: `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}` }
  }),
)
const current = computed(() => months.value[monthsAgo.value])

const monthNo = computed(() => (data.value ? ym(data.value.month).month : current.value.label.split('.')[1] * 1))

async function load() {
  loading.value = true
  error.value = ''
  try {
    await dash.loadSpending(monthsAgo.value)
    if (monthsAgo.value === 0 && !dash.diagnosis) await dash.loadDiagnosis().catch(() => {})
  } catch (e) {
    // 서버 message 는 한국어로 고정이라 그대로 쓰면 19개 언어 화면에서 한국어가 샌다.
    error.value = t(errorKey(e))
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(monthsAgo, load)

// additionalNeeded 는 "지금처럼 쓰면" 모자라는 돈이다. "줄여도 모자라요" 문장은 다 줄인 뒤의 차액이어야
// 하므로 목표(monthlyBaseline) − 최대 예상 저축(maxExpectedSaving) 을 직접 뺀다. 음수면 여유.
const gap = computed(() => (dx.value ? Number(dx.value.monthlyBaseline) - Number(dx.value.maxExpectedSaving) : null))
</script>

<template>
  <main class="sp">
    <AppHeader back :title="t('spending.title', { m: monthNo })">
      <template #right><LangSwitch /></template>
    </AppHeader>
    <div v-if="open" class="dim" @click="open = false" />

    <!-- 월 선택은 헤더 밖으로. 제목·언어 토글과 한 줄에 두면 제목이 잘린다. -->
    <div class="monthbar">
      <button type="button" class="month num" :aria-expanded="open" @click="open = !open">
        {{ current.label }}
        <svg class="chev" :class="{ up: open }" width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <ul v-if="open" class="menu" role="listbox">
        <li
          v-for="m in months"
          :key="m.monthsAgo"
          role="option"
          class="mi num"
          :class="{ on: m.monthsAgo === monthsAgo }"
          :aria-selected="m.monthsAgo === monthsAgo"
          @click="monthsAgo = m.monthsAgo; open = false"
        >
          {{ m.label }}
        </li>
      </ul>
    </div>

    <div class="body">
      <p v-if="error" class="err">{{ error }}</p>
      <p v-else-if="!data && loading" class="err">{{ t('common.loading') }}</p>

      <template v-if="data">
        <!-- 페이스 -->
        <section class="card">
          <p class="until">{{ monthsAgo === 0 ? t('spending.until_day', { m: monthNo, d: data.daysCovered }) : t('spending.whole_month', { m: monthNo }) }}</p>
          <p class="total num">{{ won(data.total) }}</p>
        </section>

        <!-- 카테고리별 소비 -->
        <section class="card">
          <div class="fh">
            <span class="fname">{{ t('spending.top_categories') }}</span>
          </div>
          <SpendingDonut v-if="data.categoryTotals.length" :categories="data.categoryTotals" :total="data.total" />
          <SpendingLegend v-if="data.categoryTotals.length" :categories="data.categoryTotals" />
          <p v-else class="empty">{{ t('spending.none') }}</p>
        </section>

        <!-- 이번 달 저축 예상 (진단 API) -->
        <section v-if="dx" class="card">
          <p class="ct">{{ t('spending.saving_title') }}</p>
          <div class="ab">
            <div class="box g">
              <p class="bl">{{ t('spending.if_keep') }}</p>
              <p class="bv num">{{ won(dx.currentExpectedSaving) }}</p>
            </div>
            <div class="box y">
              <p class="bl">{{ t('spending.if_cut') }}</p>
              <p class="bv num">{{ won(dx.maxExpectedSaving) }}</p>
            </div>
          </div>
          <!--
            두 문장을 한 문단에 담으면 붙어서 나온다. <template> 안에 넣은 뒤 공백은
            Vue 가 빌드할 때 지운다("...모을 수 있어요.줄이면 목표..."). 서로 다른 이야기이니
            줄을 나눈다 — 하나는 무엇을 줄이라는 말이고, 하나는 그래서 목표에 닿느냐는 말이다.
          -->
          <div class="sentences">
            <p v-if="dx.topSavingCategory">{{ t('spending.tip', { cat: t('cat.' + dx.topSavingCategory), amt: won(dash.savingRoom) }) }}</p>
            <p v-if="gap > 0">{{ t('spending.short', { goal: won(dx.monthlyBaseline), amt: won(gap) }) }}</p>
            <p v-else>{{ t('spending.enough', { goal: won(dx.monthlyBaseline) }) }}</p>
          </div>
        </section>
      </template>
    </div>

    <BottomNav />
  </main>
</template>

<style scoped>
.sp {
  height: 100dvh; /* 화면 높이에 고정하고 .body 만 안에서 스크롤한다 — 헤더·하단바는 화면 밖으로 밀려나지 않는다 */
  display: flex;
  flex-direction: column;
  background: var(--gray-50);
}
.monthbar {
  position: relative;
  z-index: 25;
  display: flex;
  padding: 14px 20px 0;
}
.month {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 10px 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--r-pill);
  background: var(--surface-card);
  font-size: 15px;
  font-weight: 600;
  color: var(--gray-900);
  white-space: nowrap;
}
.chev {
  color: var(--gray-500);
  transition: transform 0.15s ease;
}
.chev.up {
  transform: rotate(180deg);
}
.menu {
  position: absolute;
  left: 20px;
  top: calc(100% + 6px);
  margin: 0;
  padding: 6px;
  list-style: none;
  width: 140px;
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-menu);
}
.mi {
  padding: 10px 12px;
  border-radius: var(--r-input);
  font-size: 16px;
  color: var(--gray-900);
  cursor: pointer;
}
.mi.on {
  background: var(--yellow-50);
  font-weight: 600;
}
.dim {
  position: fixed;
  inset: 0;
  z-index: 20;
}
.body {
  flex: 1 1 0;
  min-height: 0; /* flex 항목 기본 min-height:auto 면 내용만큼 늘어나 overflow-y 가 안 먹는다 */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 20px 20px;
}
.card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border-radius: var(--r-card);
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
}
.until {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-700);
  line-height: 1.45;
}
.total {
  padding-top: 4px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--gray-900);
}
.fh {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fname {
  font-size: 14px;
  font-weight: 700;
  color: var(--gray-900);
}
.empty {
  font-size: 14px;
  color: var(--gray-400);
}
.ct {
  font-size: 16px;
  font-weight: 700;
  color: var(--gray-900);
  line-height: 1.45;
}
.ab {
  display: flex;
  gap: 12px;
  padding-top: 12px;
}
.box {
  flex: 1 0 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--r-card);
  line-height: 1.45;
}
.box.g {
  background: var(--gray-100);
}
.box.y {
  background: var(--yellow-50);
}
.bl {
  font-size: 14px;
  color: var(--gray-700);
}
.bv {
  font-size: 20px;
  font-weight: 700;
  color: var(--gray-900);
}
.sentences {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 12px;
  font-size: 14px;
  line-height: 1.45;
  color: var(--gray-700);
}
.err {
  padding: 12px;
  text-align: center;
  font-size: 14px;
  color: var(--gray-500);
}
</style>
