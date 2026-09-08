<script setup>
import { computed } from 'vue'
import { comma } from '../../utils/format'

/*
 * 카테고리별 소비 도넛 그래프. stroke-dasharray로 링을 세그먼트만큼 잘라 이어붙이는 방식
 * (라이브러리 없이 SVG로 직접 그림 — 프로젝트에 차트 라이브러리가 없고 세그먼트가 최대
 * 6개(SpendingCategory.ALL)라 굳이 의존성을 추가할 정도는 아니라고 판단).
 * 색상은 CategoryIcon.vue의 카테고리별 배경색과 맞춰서 목록 행의 점·아이콘과 시각적으로 연결된다.
 */
const props = defineProps({
  categories: { type: Array, required: true }, // [{ category, amount }], 금액 내림차순
  total: { type: [String, Number], required: true },
})

// 파스텔 팔레트로 교체 (사용자 지정). 각 팔레트에서 가장 진한 톤을 골라 얇은 링에서도
// 구분되게 하고, 5번째(기타)는 같은 톤 계열로 새로 하나 더함. 주거는 CategoryIcon.vue의
// 테라코타 톤(#c17a54)과 맞춘 파스텔.
const COLORS = {
  식비: '#F5AFAF', // pink
  통신: '#BE9FE1', // purple
  교통: '#71C9CE', // teal
  쇼핑: '#A0C49D', // green
  주거: '#E8B894', // terracotta
  기타: '#D6D3D1', // gray
}
const FALLBACK_COLOR = '#d6d3d1'

const RADIUS = 45
const STROKE = 16
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const segments = computed(() => {
  const sum = props.categories.reduce((s, c) => s + Number(c.amount), 0)
  if (sum <= 0) return []
  let offset = 0
  return props.categories.map((c) => {
    const ratio = Number(c.amount) / sum
    const length = ratio * CIRCUMFERENCE
    const seg = {
      category: c.category,
      color: COLORS[c.category] || FALLBACK_COLOR,
      dasharray: `${length} ${CIRCUMFERENCE - length}`,
      dashoffset: -offset,
    }
    offset += length
    return seg
  })
})
</script>

<template>
  <div class="donut">
    <svg viewBox="0 0 120 120" width="140" height="140" role="img" :aria-label="comma(total)">
      <circle v-if="!segments.length" cx="60" cy="60" :r="RADIUS" fill="none" :stroke="FALLBACK_COLOR" :stroke-width="STROKE" />
      <circle
        v-for="seg in segments"
        :key="seg.category"
        cx="60"
        cy="60"
        :r="RADIUS"
        fill="none"
        :stroke="seg.color"
        :stroke-width="STROKE"
        :stroke-dasharray="seg.dasharray"
        :stroke-dashoffset="seg.dashoffset"
        transform="rotate(-90 60 60)"
      />
      <text x="60" y="65" text-anchor="middle" class="center num">{{ comma(total) }}</text>
    </svg>
  </div>
</template>

<style scoped>
.donut {
  display: flex;
  justify-content: center;
  padding: 4px 0 12px;
}
.center {
  font-size: 15px;
  font-weight: 700;
  fill: var(--gray-900);
}
</style>
