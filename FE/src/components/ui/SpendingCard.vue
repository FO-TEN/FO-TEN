<script setup>
import { computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'
import { won } from '../../utils/format'
import SpendingDonut from './SpendingDonut.vue'

/*
 * 답변에 딸려 오는 이번 달 소비 카드. 소비내역 화면의 도넛과 같은 컴포넌트를 그대로 쓴다.
 * payload 는 서버가 그때 값으로 남긴 스냅샷이라 여기서 다시 계산하지 않는다 — 지난 대화를
 * 되감아도 그날의 숫자가 그대로 보여야 한다.
 *
 * 서버는 달 이름을 글자로 주지 않고 월 번호(monthValue)만 준다. "9월" 을 서버가 적어 보내면
 * 19개 언어 화면에 한국어가 그대로 나가므로, 표기는 여기서 제 언어로 고른다.
 */
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const categories = computed(() => props.payload.categoryTotals || [])
</script>

<template>
  <div class="sc">
    <div class="head">
      <span class="title">{{ t('spending.top_categories') }}</span>
      <span class="until">{{ t('spending.until_day', { m: payload.monthValue, d: payload.daysCovered }) }}</span>
    </div>
    <p class="total num">{{ won(payload.total) }}</p>

    <SpendingDonut v-if="categories.length" :categories="categories" :total="payload.total" />

    <div class="rows">
      <div v-for="c in categories" :key="c.category" class="r">
        <span class="rl">{{ t('cat.' + c.category) }}</span>
        <span class="rv num">{{ won(c.amount) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sc {
  padding: 14px;
  border-radius: var(--r-card, 14px);
  background: #fff;
  border: 1px solid var(--border-soft);
}
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.title {
  font-size: 14px;
  font-weight: 700;
  color: var(--gray-900);
}
.until {
  font-size: 11px;
  color: var(--gray-400);
}
.total {
  margin-top: 2px;
  font-size: 20px;
  font-weight: 700;
  color: var(--gray-900);
}
.rows {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.r {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.rl {
  color: var(--gray-700);
}
.rv {
  font-weight: 600;
  color: var(--gray-900);
}
</style>
