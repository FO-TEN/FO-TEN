<script setup>
import { computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'
import { won } from '../../utils/format'
import { CATEGORY_COLORS, CATEGORY_FALLBACK_COLOR } from '../../utils/categoryColors'
import SpendingDonut from './SpendingDonut.vue'

/*
 * 답변에 딸려 오는 이번 달 소비 카드. 소비내역 화면의 도넛을 그대로 쓴다.
 * payload 는 서버가 그때 값으로 남긴 스냅샷이라 여기서 다시 계산하지 않는다 — 지난 대화를
 * 되감아도 그날의 숫자가 그대로 보여야 한다.
 *
 * 머리에는 제목만 둔다. 기간("9월 8일까지")은 이 카드가 이번 달만 그리므로 굳이 적지 않는다.
 *
 * 총액은 도넛 한가운데 한 번만 쓴다. 위에도 적으면 같은 숫자가 두 번 보인다.
 * 대신 각 줄에 비중(%)을 넣는다 — 금액만 있으면 어느 쪽이 큰지 눈으로 견줘야 하는데,
 * 한 항목이 대부분을 차지하는 달에는 도넛만으로 그 차이가 읽히지 않는다.
 * 비중은 이름에 붙여 "기타(84%)" 로 읽히게 한다. 따로 떼어 세우면 줄이 셋으로 쪼개진다.
 *
 * 줄 앞의 점은 도넛 조각과 같은 색이다. 이게 없으면 그림과 목록이 따로 논다.
 */
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const rows = computed(() => {
  const list = props.payload.categoryTotals || []
  const sum = list.reduce((s, c) => s + Number(c.amount), 0)
  return list.map((c) => ({
    category: c.category,
    amount: c.amount,
    color: CATEGORY_COLORS[c.category] || CATEGORY_FALLBACK_COLOR,
    // 1% 미만도 0% 로 적지 않는다. 금액이 있는데 0 이라고 쓰면 안 쓴 것처럼 보인다.
    percent: sum > 0 ? Math.max(1, Math.round((Number(c.amount) / sum) * 100)) : 0,
  }))
})
</script>

<template>
  <section class="sc">
    <p class="st">{{ t('spending.top_categories') }}</p>

    <SpendingDonut v-if="rows.length" :categories="payload.categoryTotals" :total="payload.total" />

    <div class="rows">
      <div v-for="r in rows" :key="r.category" class="r">
        <span class="dot" :style="{ background: r.color }" />
        <span class="label">{{ t('cat.' + r.category) }}<span class="pct num">({{ r.percent }}%)</span></span>
        <span class="amt num">{{ won(r.amount) }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* 말풍선과의 간격·여백·모서리를 다른 대화 카드(RoadmapGraphCard·SegmentDetailCard)와 맞춘다. */
.sc {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
  padding: 18px 16px 16px;
  border-radius: 16px;
  background: var(--surface-card);
}
.st {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}
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
