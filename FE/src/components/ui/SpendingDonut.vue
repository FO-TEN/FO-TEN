<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { won } from '../../utils/format'
import { CATEGORY_COLORS as COLORS, CATEGORY_FALLBACK_COLOR as FALLBACK_COLOR } from '../../utils/categoryColors'

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

const RADIUS = 45
const STROKE = 20
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

// 가운데 총액은 ₩ 를 달고 쓴다 — 소비내역 화면의 표기 규칙이다(utils/format 주석).
const centerText = computed(() => won(props.total))

const BASE_FONT = 12
const MIN_FONT = 7
// 링 안쪽 지름에서 좌우로 2씩 남긴 폭. 글자가 링에 닿으면 겹쳐 보인다.
const MAX_TEXT_WIDTH = 2 * (RADIUS - STROKE / 2) - 4

const centerEl = ref(null)
const centerSize = ref(BASE_FONT)

/*
 * 총액은 자릿수가 늘면 링 안쪽을 넘는다(₩12,345,678 처럼). 글자 수로 어림잡으면 통화 기호·
 * 글꼴이 바뀔 때 또 어긋나므로, 기본 크기로 한 번 그려 놓고 실제 폭을 재서 그만큼만 줄인다.
 * 폭은 글자 크기에 정비례하니 한 번 재고 한 번 줄이면 끝난다.
 */
async function fitCenter() {
  centerSize.value = BASE_FONT
  await nextTick()

  const width = measureCenter()
  if (!width || width <= MAX_TEXT_WIDTH) return
  centerSize.value = Math.max(MIN_FONT, Math.floor((BASE_FONT * MAX_TEXT_WIDTH) / width))
}

function measureCenter() {
  try {
    return centerEl.value?.getBBox().width || 0
  } catch {
    // 화면에 붙기 전(숨은 상태)에는 잴 수 없다. 그때는 기본 크기로 둔다.
    return 0
  }
}

onMounted(fitCenter)
watch(centerText, fitCenter)

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
    <svg viewBox="0 0 120 120" width="140" height="140" role="img" :aria-label="centerText">
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
      <text ref="centerEl" x="60" y="65" text-anchor="middle" class="center num" :font-size="centerSize">{{ centerText }}</text>
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
  font-weight: 700;
  fill: var(--gray-900);
}
</style>
