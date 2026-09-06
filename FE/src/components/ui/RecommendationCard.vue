<script setup>
import { useLocaleStore } from '../../stores/locale'
import { won } from '../../utils/format'

// 답변에 딸려 오는 추천 조합 카드 (UI 흐름 v5 [첫 운용구간 추천 조합]).
// payload 는 서버가 그때 값으로 남긴 스냅샷이라 여기서 다시 계산하지 않는다.
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

// 아직 계산 전이면 null 로 온다. 0 으로 읽히지 않게 값이 있을 때만 그린다.
const has = (v) => v !== null && v !== undefined && Number(v) > 0
</script>

<template>
  <section class="reco">
    <p class="rt">{{ t('card.reco_title') }}</p>

    <div class="base">
      <span class="bl">{{ t('card.monthly_base') }}</span>
      <span class="bv num">{{ won(payload.monthlyBaseline) }}</span>
    </div>

    <div class="rows">
      <div v-for="s in payload.savings || []" :key="s.productName" class="r">
        <div class="left">
          <span class="pn">{{ s.productName }}</span>
          <span class="meta num">{{ t('card.months', { n: s.termMonths }) }} · {{ t('card.rate', { r: s.appliedRate }) }}</span>
        </div>
        <span class="amt num">{{ won(s.monthlyAllocated) }}</span>
      </div>

      <div v-if="payload.deposit" class="r">
        <div class="left">
          <span class="pn">{{ payload.deposit.productName }}</span>
          <span class="meta num">
            {{ t('card.deposit') }} · {{ t('card.months', { n: payload.deposit.termMonths }) }}
            · {{ t('card.rate', { r: payload.deposit.appliedRate }) }}
          </span>
        </div>
        <span class="amt num">{{ won(payload.deposit.principal) }}</span>
      </div>

      <div v-if="has(payload.recommendedCashSaving)" class="r cash">
        <div class="left"><span class="pn">{{ t('card.cash') }}</span></div>
        <span class="amt num">{{ won(payload.recommendedCashSaving) }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.reco {
  margin-top: 6px;
  padding: 14px 16px;
  border-radius: var(--r-card);
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
}
.rt {
  font-size: 13px;
  font-weight: 700;
  color: var(--gray-900);
}
.base {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0 10px;
  border-bottom: 1px solid var(--border-soft);
}
.bl {
  font-size: 13px;
  color: var(--gray-600);
}
.bv {
  font-size: 17px;
  font-weight: 700;
  color: var(--gray-900);
}
.rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 10px;
}
.r {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.pn {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  font-size: 12px;
  color: var(--gray-500);
}
.amt {
  flex: 0 0 auto;
  font-size: 15px;
  font-weight: 600;
  color: var(--gray-900);
}
.cash .pn,
.cash .amt {
  color: var(--gray-500);
}
</style>
