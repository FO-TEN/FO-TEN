<script setup>
import { computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'
import { comma } from '../../utils/format'

// 답변에 딸려 오는 구간 확대 카드 (Figma 07b/07c_대화 · 로드맵 217:2730).
// 서버가 구간 막대(4-8)와 이번 달 배분(4-9)을 한 payload 로 합쳐 보낸다.
// 그때 값을 그대로 남긴 스냅샷이라 여기서 다시 계산하지 않는다.
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

// 시안 치수 (375 프레임 기준)
const CHART_H = 144 // 축선까지의 높이
const MAX_BAR_H = 92 // 가장 높은 막대
const MIN_BAR_H = 10

const n = (v) => (v === null || v === undefined ? 0 : Number(v))
const has = (v) => v !== null && v !== undefined && Number(v) > 0
// 만 단위. 막대 폭이 좁아 원 단위는 들어가지 않는다.
const man = (v) => t('card.man', { n: comma(Math.round(n(v) / 10000)) })

const baseline = computed(() => n(props.payload.baselineAmount))
const bars = computed(() => props.payload.bars || [])
const allocations = computed(() => props.payload.allocations || [])

// 밀린 금액을 어떻게 채우기로 했는지. NONE 이면 배지를 달지 않는다.
const choiceLabel = computed(() => {
  const c = props.payload.deficitChoice
  if (c === 'SPREAD') return t('detail.spread')
  if (c === 'FULL_RECOVERY') return t('detail.full_recovery')
  return ''
})

const tallest = computed(() => Math.max(baseline.value, ...bars.value.map((b) => n(b.amount)), 1))
const scale = computed(() => MAX_BAR_H / tallest.value)

const rows = computed(() =>
  bars.value.map((b) => {
    const amount = n(b.amount)
    const diff = amount - baseline.value
    return {
      key: b.type,
      // 서버 label 은 한국어라 그대로 쓰면 다른 언어에서 안 바뀐다. 종류로 골라 옮긴다.
      label: t('detail.bar_' + b.type.toLowerCase()),
      actual: b.type === 'ACTUAL',
      amount,
      height: Math.max(MIN_BAR_H, Math.round(amount * scale.value)),
      diff,
    }
  }),
)

// 목표기준액 선. 막대와 같은 자를 쓴다.
const baselineTop = computed(() => CHART_H - Math.round(baseline.value * scale.value))
// "지금" 표시는 이번 달 막대 앞에 선다. 지난달이 없으면 맨 앞이다.
const nowIndex = computed(() => Math.max(0, rows.value.findIndex((r) => !r.actual)))

function diffText(v) {
  return (v > 0 ? '+' : '−') + man(Math.abs(v))
}
</script>

<template>
  <section class="sd">
    <div class="head">
      <div class="ht">
        <p class="st">{{ t('detail.title', { n: payload.segmentNo }) }}</p>
        <span v-if="choiceLabel" class="tag">{{ choiceLabel }}</span>
      </div>
      <p class="ss">{{ t('detail.sub') }}</p>
    </div>

    <div class="chart" :style="{ height: CHART_H + 26 + 'px' }">
      <!-- 목표기준액 기준선 -->
      <div class="base-line" :style="{ top: baselineTop + 'px' }" />
      <span class="base-tag" :style="{ top: Math.max(0, baselineTop - 22) + 'px' }">
        {{ t('detail.baseline', { v: man(baseline) }) }}
      </span>

      <div class="cols">
        <div v-for="(r, i) in rows" :key="r.key" class="col">
          <span v-if="i === nowIndex" class="now">{{ t('detail.now') }}</span>
          <span class="amt num" :class="{ inbar: r.actual }" :style="r.actual ? { bottom: r.height - 26 + 'px' } : {}">
            {{ man(r.amount) }}
          </span>
          <div class="bar" :class="r.actual ? 'actual' : 'plan'" :style="{ height: r.height + 'px' }">
            <span v-if="!r.actual" class="plan-tx">{{ t('detail.plan') }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="axis">
      <div v-for="(r, i) in rows" :key="'ax-' + r.key" class="ax">
        <span class="ax-l" :class="{ on: i === nowIndex }">{{ r.label }}</span>
        <span v-if="r.diff !== 0" class="pill" :class="r.diff > 0 ? 'up' : 'down'">{{ diffText(r.diff) }}</span>
      </div>
    </div>

    <div class="alloc">
      <p class="al-t">{{ t('detail.alloc_title', { v: man(payload.monthlySavingAmount) }) }}</p>
      <div v-for="a in allocations" :key="a.productName" class="al-r">
        <span class="al-n">{{ a.productName }} · {{ a.appliedRate }}%</span>
        <span class="al-v">
          <b class="num">{{ man(a.allocatedAmount) }}</b>
          <span class="al-lim">{{ t('detail.limit', { v: man(a.monthlyLimit) }) }}</span>
        </span>
      </div>
      <div v-if="has(payload.recommendedCashSaving)" class="al-r">
        <span class="al-n">{{ t('card.cash') }}</span>
        <span class="al-v">
          <b class="num">{{ man(payload.recommendedCashSaving) }}</b>
          <span class="al-lim">{{ t('detail.over_limit') }}</span>
        </span>
      </div>
    </div>

    <div class="legend">
      <span class="lg"><i class="dot actual" />{{ t('detail.legend_actual') }}</span>
      <span class="lg"><i class="dot plan" />{{ t('detail.legend_plan') }}</span>
    </div>
  </section>
</template>

<style scoped>
.sd {
  --c-actual: #ff9494;
  --c-plan: #ffd1d1;
  --c-plan-text: #b04a4a;

  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 6px;
  padding: 18px 16px 16px;
  border-radius: 16px;
  background: var(--surface-card);
}
.head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ht {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.st {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}
.tag {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: var(--r-pill);
  background: var(--green-bg);
  color: var(--green);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}
.ss {
  font-size: 14px;
  line-height: 1.4;
  color: var(--gray-500);
}

/* 막대 세 개와 목표기준액 기준선 */
.chart {
  position: relative;
  padding-top: 26px;
  border-bottom: 1px solid var(--gray-200);
}
.base-line {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px dashed var(--gray-400);
}
.base-tag {
  position: absolute;
  right: 0;
  padding: 2px 7px;
  border-radius: 6px;
  background: var(--gray-100);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-700);
  white-space: nowrap;
}
.cols {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  height: 100%;
}
.col {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}
.now {
  position: absolute;
  top: -26px;
  left: 0;
  padding-left: 2px;
  border-left: 2px dashed var(--gray-850);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}
.amt {
  margin-bottom: 2px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--gray-850);
  white-space: nowrap;
}
/* 지난달 막대는 색이 진해 숫자를 안에 흰 글자로 넣는다 */
.amt.inbar {
  position: absolute;
  margin: 0;
  color: #fff;
}
.bar {
  width: 100%;
  border-radius: 8px 8px 0 0;
}
.bar.actual {
  background: var(--c-actual);
}
.bar.plan {
  display: flex;
  justify-content: center;
  background: var(--c-plan);
}
.plan-tx {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--c-plan-text);
}

.axis {
  display: flex;
  gap: 14px;
  margin-top: -6px;
}
.ax {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.ax-l {
  font-size: 12px;
  line-height: 1.4;
  color: var(--gray-700);
  overflow-wrap: anywhere;
  text-align: center;
}
.ax-l.on {
  font-weight: 700;
  color: var(--gray-850);
}
.pill {
  padding: 2px 7px;
  border-radius: var(--r-pill);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}
.pill.up {
  background: var(--green-bg);
  color: var(--green);
}
.pill.down {
  background: var(--red-bg);
  color: var(--red);
}

.alloc {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--gray-100);
}
.al-t {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-700);
}
.al-r {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.al-n {
  min-width: 0;
  font-size: 14px;
  line-height: 1.4;
  color: var(--gray-850);
  overflow-wrap: anywhere;
}
.al-v {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}
.al-v b {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--gray-850);
}
.al-lim {
  font-size: 12px;
  line-height: 1.4;
  color: var(--gray-400);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
}
.lg {
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
.dot.actual {
  background: var(--c-actual);
}
.dot.plan {
  background: var(--c-plan);
}
</style>
