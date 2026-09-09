<script setup>
import { computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'
import SpendingDonut from './SpendingDonut.vue'
import SpendingLegend from './SpendingLegend.vue'

/*
 * 답변에 딸려 오는 이번 달 소비 카드. 소비내역 화면의 도넛을 그대로 쓴다.
 * payload 는 서버가 그때 값으로 남긴 스냅샷이라 여기서 다시 계산하지 않는다 — 지난 대화를
 * 되감아도 그날의 숫자가 그대로 보여야 한다.
 *
 * 머리에는 제목만 둔다. 기간("9월 8일까지")은 이 카드가 이번 달만 그리므로 굳이 적지 않는다.
 *
 * 총액은 도넛 한가운데 한 번만 쓴다. 위에도 적으면 같은 숫자가 두 번 보인다.
 * 항목별 목록은 소비내역 화면과 같은 것이라 SpendingLegend 로 뺐다.
 */
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const categories = computed(() => props.payload.categoryTotals || [])
</script>

<template>
  <section class="sc">
    <p class="st">{{ t('spending.top_categories') }}</p>

    <SpendingDonut v-if="categories.length" :categories="categories" :total="payload.total" />
    <SpendingLegend :categories="categories" />
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
</style>
