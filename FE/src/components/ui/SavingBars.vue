<script setup>
import { computed } from 'vue'
import { comma } from '../../utils/format'

/*
 * 대시보드 "이번 달 저축" 막대 2개 (Figma 217:3083 Columns, 295x140).
 *
 * ▶ 데이터 연결
 *   current = diagnosis.currentExpectedSaving   (지금 소비 속도로 예상되는 저축)
 *   saving  = diagnosis.maxExpectedSaving       (최대한 줄였을 때)
 *   target  = diagnosis.monthlyBaseline         (매달 필요 = 목표기준액)
 *   GET /api/goals/current/diagnosis 한 번으로 셋 다 온다. 값이 바뀌면 막대 높이만 다시 계산된다.
 *
 * 높이 규칙: 목표(점선) 를 84px 로 두고 비례. 목표를 넘으면 최대 100px 까지.
 * 목표 금액 문구는 점선 위가 아니라 차트 위 범례에 둔다 — 막대가 점선을 넘으면 글자를 가린다.
 */
const props = defineProps({
  current: { type: [Number, String], required: true },
  saving: { type: [Number, String], required: true },
  target: { type: [Number, String], required: true },
  labelCurrent: { type: String, default: '현재' },
  labelSaving: { type: String, default: '절약' },
  labelTarget: { type: String, default: '매달 필요' },
  unit: { type: String, default: '원' },
})

const TARGET_H = 84
const MAX_H = 100
const h = (v) => {
  const t = Number(props.target) || 1
  return Math.max(6, Math.min(MAX_H, Math.round((Number(v) / t) * TARGET_H)))
}
const bars = computed(() => [
  { key: 'current', v: Number(props.current), h: h(props.current), color: 'var(--gray-300)', label: props.labelCurrent },
  { key: 'saving', v: Number(props.saving), h: h(props.saving), color: 'var(--surface-primary)', label: props.labelSaving },
])
</script>

<template>
  <div
    class="wrap"
    role="img"
    :aria-label="`${labelCurrent} ${comma(current)}, ${labelSaving} ${comma(saving)}, ${labelTarget} ${comma(target)}`"
  >
    <p class="legend">
      <span class="dash" aria-hidden="true" />
      <span class="num">{{ labelTarget }} {{ comma(target) }} {{ unit }}</span>
    </p>

    <div class="chart">
      <div class="target" :style="{ bottom: 28 + TARGET_H + 'px' }" />
      <div class="base" />
      <div v-for="b in bars" :key="b.key" class="col">
        <div class="bar" :style="{ height: b.h + 'px', background: b.color }">
          <span class="val num">{{ comma(b.v) }}</span>
        </div>
        <span class="xl">{{ b.label }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
}
.legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding-bottom: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--gray-700);
  line-height: 1.4;
}
.dash {
  flex: 0 0 auto;
  width: 16px;
  border-top: 1.5px dashed var(--gray-400);
}
.chart {
  position: relative;
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  width: 100%;
  height: 140px;
  padding: 0 20px;
}
.base {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 28px;
  height: 1px;
  background: var(--border-soft);
}
.target {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1.5px dashed var(--gray-400);
}
.col {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 60px;
  padding-bottom: 28px;
}
.bar {
  width: 60px;
  border-radius: var(--r-input) var(--r-input) 0 0;
  display: flex;
  justify-content: center;
  transition: height 0.4s ease;
}
.val {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--gray-850);
}
.xl {
  position: absolute;
  bottom: 0;
  font-size: 14px;
  color: var(--gray-700);
}
</style>
