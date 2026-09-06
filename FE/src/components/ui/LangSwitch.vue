<script setup>
import { useLocaleStore } from '../../stores/locale'

// Figma LangSwitch(99:12) 를 한 칸짜리 토글로 줄였다. 두 칸 세그먼트는 헤더에서 너무 컸고,
// 어차피 선택지가 한국어 ↔ 내 언어 둘뿐이라 누르면 반대쪽으로 넘어가는 편이 빠르다.
// 18개 언어 선택은 LangToggle(로그인·회원가입)에서만 한다.
const locale = useLocaleStore()
</script>

<template>
  <button
    v-if="locale.myLang !== 'ko'"
    type="button"
    class="switch"
    :title="locale.isKorean ? locale.myLangLabel : '한국어'"
    :aria-label="`언어 전환: ${locale.isKorean ? locale.myLangLabel : '한국어'}`"
    @click="locale.toggle()"
  >
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
      <path d="M3 12h18M12 3c2.8 3 2.8 15 0 18M12 3c-2.8 3-2.8 15 0 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    </svg>
    <span class="label">{{ locale.isKorean ? '한국어' : locale.myLangLabel }}</span>
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M7 10l5-5 5 5M17 14l-5 5-5-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  </button>
</template>

<style scoped>
.switch {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 9px 0 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--r-pill);
  background: var(--surface-card);
  color: var(--gray-700);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}
.switch:active {
  background: var(--gray-50);
}
.label {
  max-width: 84px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
