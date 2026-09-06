<script setup>
/*
 * 금액 입력 단위 토글 — 원(KRW) ↔ 본국 통화. LangSwitch 와 같은 한 칸 알약이고 누르면 반대쪽으로 넘어간다.
 * 통화 기호는 겹치는 게 많아서(Rs 가 네팔·스리랑카·파키스탄) 기호 + 코드를 함께 보여준다.
 */
const props = defineProps({
  modelValue: { type: String, required: true }, // 'KRW' | 본국 통화 코드
  currency: { type: Object, required: true }, // { code, symbol }
  disabled: { type: Boolean, default: false }, // 환율이 없으면 못 바꾼다
})
const emit = defineEmits(['update:modelValue'])

function flip() {
  emit('update:modelValue', props.modelValue === 'KRW' ? props.currency.code : 'KRW')
}
</script>

<template>
  <button
    type="button"
    class="unit"
    :disabled="disabled"
    :aria-label="`단위 전환: ${modelValue === 'KRW' ? currency.code : 'KRW'}`"
    @click="flip"
  >
    <span class="sym">{{ modelValue === 'KRW' ? '₩' : currency.symbol }}</span>
    <span class="code">{{ modelValue === 'KRW' ? 'KRW' : currency.code }}</span>
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M7 10l5-5 5 5M17 14l-5 5-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  </button>
</template>

<style scoped>
.unit {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px 0 9px;
  border: 1px solid var(--border-strong);
  border-radius: var(--r-pill);
  background: var(--surface-card);
  color: var(--gray-700);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}
.unit:active {
  background: var(--gray-50);
}
.unit:disabled {
  opacity: 0.45;
  cursor: default;
}
.sym {
  font-size: 13px;
}
.code {
  letter-spacing: 0.2px;
}
</style>
