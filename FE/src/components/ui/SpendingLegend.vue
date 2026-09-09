<script setup>
import { computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'
import { won } from '../../utils/format'
import { CATEGORY_COLORS, CATEGORY_FALLBACK_COLOR } from '../../utils/categoryColors'

/*
 * 도넛 아래에 붙는 항목별 목록. 소비내역 화면과 대화 카드가 같은 것을 그리므로 한 곳에 둔다.
 * 한쪽만 고치면 같은 그래프를 설명하는 목록이 화면마다 달라진다.
 *
 * 줄 앞의 점은 도넛 조각과 같은 색이다. 이게 없으면 그림과 목록이 따로 논다.
 *
 * 비중(%)을 함께 적는 이유: 금액만 있으면 어느 쪽이 큰지 눈으로 견줘야 하는데, 한 항목이
 * 대부분을 차지하는 달에는 도넛만으로 그 차이가 읽히지 않는다. 이름에 붙여 "기타(84%)" 로
 * 읽히게 한다 — 따로 떼어 세우면 줄이 셋으로 쪼개져 눈이 가운데서 한 번 끊긴다.
 */
const props = defineProps({
  categories: { type: Array, required: true }, // [{ category, amount }], 금액 내림차순
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const rows = computed(() => {
  const sum = props.categories.reduce((s, c) => s + Number(c.amount), 0)
  return props.categories.map((c) => ({
    category: c.category,
    amount: c.amount,
    color: CATEGORY_COLORS[c.category] || CATEGORY_FALLBACK_COLOR,
    // 1% 미만도 0% 로 적지 않는다. 금액이 있는데 0 이라고 쓰면 안 쓴 것처럼 보인다.
    percent: sum > 0 ? Math.max(1, Math.round((Number(c.amount) / sum) * 100)) : 0,
  }))
})
</script>

<template>
  <div class="rows">
    <div v-for="r in rows" :key="r.category" class="r">
      <span class="dot" :style="{ background: r.color }" />
      <span class="label">{{ t('cat.' + r.category) }}<span class="pct num">({{ r.percent }}%)</span></span>
      <span class="amt num">{{ won(r.amount) }}</span>
    </div>
  </div>
</template>

<style scoped>
.rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 4px;
}
.r {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1.4;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
/* 이름은 언어마다 길이가 크게 다르다. 남는 자리를 이름이 갖고, 넘치면 자른다. */
.label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--gray-700);
}
/* 비중은 이름의 일부처럼 붙되, 한 톤 흐리게 두어 이름이 먼저 읽히게 한다. */
.pct {
  margin-left: 3px;
  font-size: 12px;
  color: var(--gray-400);
}
.amt {
  flex: none;
  text-align: right;
  font-weight: 600;
  color: var(--gray-850);
}
</style>
