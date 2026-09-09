<script setup>
import { useLocaleStore } from '../../stores/locale'
import { comma, won } from '../../utils/format'

// 구간이 끝난 달의 첫 답에 딸려 오는 카드 (UI 흐름 v5 §3). 지난 구간에서 모은 돈과 지난달 저축 결과를
// 보여준다. 채울 방식을 묻는 말은 카드 아래 말풍선이 하고, 칩은 그 밑에 붙는다.
const props = defineProps({
  payload: { type: Object, required: true },
})
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)

const n = (v) => (v === null || v === undefined ? 0 : Number(v))
const short = () => n(props.payload.shortfallAmount)
</script>

<template>
  <section class="se">
    <p class="st">{{ t('card.segment_end_title', { n: payload.segmentNo }) }}</p>

    <div class="row">
      <div class="lb">
        <span>{{ t('card.segment_end_rollover') }}</span>
      </div>
      <span class="big num">{{ won(payload.rolloverAmount) }}</span>
    </div>

    <div class="row last">
      <div class="lb">
        <span>{{ t('card.segment_end_last_month') }}</span>
        <span class="note">{{ t('card.segment_end_planned') }} : {{ won(payload.plannedAmount) }}</span>
        <span class="note">{{ t('card.segment_end_actual') }} : {{ won(payload.actualAmount) }}</span>
      </div>
      <span v-if="short() > 0" class="pill down num">−{{ comma(short()) }}</span>
      <span v-else class="pill flat">{{ t('card.segment_end_ok') }}</span>
    </div>
  </section>
</template>

<style scoped>
.se {
  margin-top: 6px;
  padding: 14px 16px;
  border-radius: var(--r-card);
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
}
.st {
  font-size: 16px;
  font-weight: 700;
  color: var(--gray-900);
  padding-bottom: 8px;
  border-bottom: 2px solid var(--border-strong);
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-soft);
}
.row.last {
  border-bottom: 0;
  padding-bottom: 2px;
}
.lb {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--gray-900);
}
.note {
  font-size: 12px;
  font-weight: 400;
  color: var(--gray-500);
  word-break: keep-all;
}
.big {
  flex: 0 0 auto;
  font-size: 17px;
  font-weight: 700;
  color: var(--gray-900);
}
.pill {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: var(--r-pill);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}
.pill.down {
  background: var(--red-bg);
  color: var(--red);
}
.pill.flat {
  background: var(--gray-100);
  color: var(--gray-700);
  font-size: 12px;
}
</style>
