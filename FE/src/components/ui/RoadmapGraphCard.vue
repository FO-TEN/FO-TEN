<script setup>
import { computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'
import { comma } from '../../utils/format'

// 답변에 딸려 오는 전체 로드맵 카드 (Figma 07b_대화 · 로드맵 217:2662).
// payload 는 서버가 그때 값으로 남긴 스냅샷이라 여기서 다시 계산하지 않는다.
// 지난 구간은 실제 값, 앞으로의 구간은 지금 조건이 이어진다고 본 값이다.
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

// 시안 치수 (375 프레임 기준). 막대 영역 높이와 가장 높은 막대의 높이.
const CHART_H = 160
const TOP_LABEL_H = 21 // total label above the last bar
const BADGE_H = 32 // in-progress badge above the active bar
const BASE_RESERVE = 24 // Figma: 160 - 136
const THIN = 4 // 값이 있는데 너무 얇으면 보이게 하는 최소 두께
const LABEL_MIN_H = 18 // 이보다 낮은 층에는 글자를 넣지 않는다

const n = (v) => (v === null || v === undefined ? 0 : Number(v))
// 만 단위로 줄인다. 막대 폭이 좁아 원 단위는 들어가지 않는다.
const man = (v) => t('card.man', { n: comma(Math.round(n(v) / 10000)) })

const segments = computed(() => props.payload.segments || [])
const totalMonths = computed(() => n(props.payload.totalMonths))
const tallest = computed(() =>
  Math.max(
    1,
    ...segments.value.map((s) => n(s.savingsAmount) + n(s.depositAmount) + n(s.cashAmount) + n(s.interestAmount)),
  ),
)

// 12개월 구간은 "N년차", 나머지는 "N개월". 마지막이면 "마지막 N개월".
function label(s, i) {
  const last = i === segments.value.length - 1
  if (last && segments.value.length > 1) return t('card.last_months', { n: s.months })
  if (s.months === 12) return t('card.year_n', { n: i + 1 })
  return t('card.months', { n: s.months })
}

const bars = computed(() =>
  segments.value.map((s, i) => {
    const total = n(s.savingsAmount) + n(s.depositAmount) + n(s.cashAmount) + n(s.interestAmount)
    const scale = (CHART_H - reserve.value) / tallest.value
    const h = (v) => (n(v) > 0 ? Math.max(THIN, Math.round(n(v) * scale)) : 0)
    return {
      key: s.segmentNo,
      grow: s.months || 1,
      active: s.status === 'ACTIVE',
      last: i === segments.value.length - 1,
      label: label(s, i),
      total,
      layers: [
        { k: 'interest', h: h(s.interestAmount), text: '' },
        { k: 'cash', h: h(s.cashAmount), text: '' },
        { k: 'deposit', h: h(s.depositAmount), text: man(s.depositAmount) },
        { k: 'savings', h: h(s.savingsAmount), text: man(s.savingsAmount) },
      ].filter((l) => l.h > 0),
    }
  }),
)

// Figma never puts the badge and the total on one bar. A one-segment roadmap does,
// so reserve room for both or the bar overflows into the subtitle.
const reserve = computed(() => {
  const segs = segments.value
  const tallestIdx = segs.reduce((best, s, i, arr) => {
    const tot = (x) => n(x.savingsAmount) + n(x.depositAmount) + n(x.cashAmount) + n(x.interestAmount)
    return tot(s) > tot(arr[best]) ? i : best
  }, 0)
  const s = segs[tallestIdx]
  if (!s) return BASE_RESERVE
  const last = tallestIdx === segs.length - 1
  const active = s.status === 'ACTIVE'
  return Math.max(BASE_RESERVE, (last ? TOP_LABEL_H : 0) + (active ? BADGE_H : 0))
})

const subtitle = computed(() =>
  t('card.roadmap_sub', { n: totalMonths.value, segs: segments.value.map((s) => s.months).join(' + ') }),
)
</script>

<template>
  <section class="rg">
    <div class="head">
      <p class="rt">{{ t('card.roadmap_title') }}</p>
      <p class="rs">{{ subtitle }}</p>
    </div>

    <div class="chart" :style="{ height: CHART_H + 'px' }">
      <div v-for="b in bars" :key="b.key" class="col" :style="{ flexGrow: b.grow }">
        <span v-if="b.active" class="badge" :class="{ stacked: b.last }">{{ t('card.in_progress') }}</span>
        <p v-if="b.last" class="top num">{{ t('card.about', { v: man(b.total) }) }}</p>
        <div class="stack">
          <div
            v-for="l in b.layers"
            :key="l.k"
            class="layer"
            :class="l.k"
            :style="{ height: l.h + 'px' }"
          >
            <span v-if="l.text && l.h >= LABEL_MIN_H" class="lt">{{ l.text }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="axis">
      <p v-for="b in bars" :key="b.key" class="ax" :style="{ flexGrow: b.grow }">{{ b.label }}</p>
    </div>

    <div class="legend">
      <span class="pill"><i class="dot savings" />{{ t('card.legend_savings') }}</span>
      <span class="pill"><i class="dot deposit" />{{ t('card.legend_deposit') }}</span>
      <span class="pill"><i class="dot cash" />{{ t('card.legend_cash') }}</span>
      <span class="pill"><i class="dot interest" />{{ t('card.legend_interest') }}</span>
    </div>

    <p class="note">{{ t('card.roadmap_note') }}</p>

    <div class="foot">
      <span class="fl">{{ t('card.roadmap_after', { n: totalMonths }) }}</span>
      <span class="fr">
        <b class="num">{{ comma(Math.round(n(payload.finalAmount) / 10000)) }}</b>
        <span>{{ t('card.roadmap_total', { i: comma(Math.round(n(payload.expectedInterestTotal) / 10000)) }) }}</span>
      </span>
    </div>
  </section>
</template>

<style scoped>
/* 차트 전용 색. 토큰에 없는 값이라 여기서만 쓴다. */
.rg {
  --c-savings: #ffb4b4;
  --c-savings-text: #7a2e2e;
  --c-deposit: #9ac1ed;
  --c-deposit-text: #194474;
  --c-cash: #d6d3d1;
  --c-interest: #ffe7a3;

  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 6px;
  padding: 18px 16px 16px;
  border-radius: var(--r-card);
  background: var(--surface-card);
}
.head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rt {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}
.rs {
  font-size: 14px;
  line-height: 1.4;
  color: var(--gray-500);
}

/* 막대: 폭은 개월 수에 비례, 높이는 가장 큰 구간을 기준으로 잰다 */
.chart {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  border-bottom: 1px solid var(--gray-200);
}
.col {
  position: relative;
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
}
.stack {
  display: flex;
  flex-direction: column;
  width: 100%;
  overflow: hidden;
  border-radius: var(--r-input);
}
.layer {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.layer.savings { background: var(--c-savings); }
.layer.deposit { background: var(--c-deposit); }
.layer.cash { background: var(--c-cash); }
.layer.interest { background: var(--c-interest); }
.lt {
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
}
.savings .lt { color: var(--c-savings-text); }
.deposit .lt { color: var(--c-deposit-text); }
.top {
  margin-bottom: 1px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--gray-850);
  white-space: nowrap;
}
.badge {
  margin: 0 0 10px;
  padding: 2px 7px;
  border-radius: var(--r-pill);
  background: var(--gray-850);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}
/* 총액이 같이 있으면 배지는 그 위에 온다. 붙어 보이지 않게 살짝 띄운다 */
.badge.stacked {
  margin-bottom: 4px;
}
.axis {
  display: flex;
  gap: 6px;
  margin-top: -6px;
}
.ax {
  flex: 1 1 0;
  min-width: 0;
  text-align: center;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-500);
  overflow-wrap: anywhere;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
}
.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px 4px 8px;
  border-radius: var(--r-pill);
  background: var(--gray-100);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-700);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.savings { background: var(--c-savings); }
.dot.deposit { background: var(--c-deposit); }
.dot.cash { background: var(--c-cash); }
.dot.interest { background: var(--c-interest); }

.note {
  font-size: 12px;
  line-height: 1.4;
  color: var(--gray-500);
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--switch-bg);
}
.fl {
  font-size: 13px;
  line-height: 1.4;
  color: var(--gray-700);
}
.fr {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font-size: 13px;
  color: var(--gray-700);
}
.fr b {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--gray-850);
}
</style>
