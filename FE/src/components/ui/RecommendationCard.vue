<script setup>
import { useRouter } from 'vue-router'
import { useLocaleStore } from '../../stores/locale'
import { won } from '../../utils/format'
import { PRODUCT_PALETTE } from '../../utils/productColors'
import chev from '../../assets/icons/product_chev12.svg'

// 답변에 딸려 오는 추천 조합 카드 (UI 흐름 v5 [첫 운용구간 추천 조합]).
// payload 는 서버가 그때 값으로 남긴 스냅샷이라 여기서 다시 계산하지 않는다.
const props = defineProps({
  payload: { type: Object, required: true },
})
const router = useRouter()
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

// 아직 계산 전이면 null 로 온다. 0 으로 읽히지 않게 값이 있을 때만 그린다.
const has = (v) => v !== null && v !== undefined && Number(v) > 0
</script>

<template>
  <!-- 누르면 상품 추천 상세로. 카드는 요약이고 자세한 건 그 화면이 보여준다 -->
  <section class="reco" role="button" tabindex="0" @click="router.push({ name: 'products' })" @keydown.enter="router.push({ name: 'products' })">
    <p class="rt">{{ t('card.reco_title') }}</p>

    <div class="base">
      <span class="bl">{{ t('card.monthly_base') }}</span>
      <span class="bv num">{{ won(payload.monthlyBaseline) }}</span>
    </div>

    <div class="rows">
      <div v-for="(s, i) in payload.savings || []" :key="s.productName" class="r">
        <div class="left">
          <i class="dot" :style="{ background: PRODUCT_PALETTE.monthly[i % PRODUCT_PALETTE.monthly.length] }" />
          <span class="pn">{{ s.productName }}</span>
        </div>
        <span class="amt num">{{ won(s.monthlyAllocated) }}</span>
      </div>

      <template v-if="payload.deposit">
        <p class="lump-lb">{{ t('card.lump') }}</p>
        <div class="r">
          <div class="left">
            <i class="dot" :style="{ background: PRODUCT_PALETTE.lump[0] }" />
            <span class="pn">{{ payload.deposit.productName }}</span>
          </div>
          <span class="amt num">{{ won(payload.deposit.principal) }}</span>
        </div>
      </template>

      <div v-if="has(payload.recommendedCashSaving)" class="r cash">
        <div class="left"><span class="pn">{{ t('card.cash') }}</span></div>
        <span class="amt num">{{ won(payload.recommendedCashSaving) }}</span>
      </div>
    </div>

    <!-- 카드 전체가 눌리지만, 눌린다는 느낌이 없어 안내 줄을 둔다 -->
    <div class="more">
      <span>{{ t('card.detail') }}</span>
      <img :src="chev" alt="" width="12" height="12" />
    </div>
  </section>
</template>

<style scoped>
.reco {
  cursor: pointer;
  margin-top: 6px;
  padding: 14px 16px;
  border-radius: var(--r-card);
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
}
.rt {
  font-size: 16px;
  font-weight: 700;
  color: var(--gray-900);
  padding-bottom: 8px;
  border-bottom: 2px solid var(--border-strong);
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
  font-weight: 500;
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
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}
.dot {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  margin-top: 4px;
  border-radius: 50%;
}
.pn {
  flex: 1 1 auto;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-900);
  overflow-wrap: anywhere;
}
.amt {
  flex: 0 0 auto;
  font-size: 15px;
  font-weight: 600;
  color: var(--gray-900);
}
.lump-lb {
  padding-top: 8px;
  border-top: 2px solid var(--border-strong);
  font-size: 13px;
  font-weight: 500;
  color: var(--gray-600);
}
.cash .pn,
.cash .amt {
  color: var(--gray-500);
}
.more {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 2px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 2px solid var(--border-strong);
  font-size: 13px;
  font-weight: 500;
  color: var(--gray-600);
}
</style>
