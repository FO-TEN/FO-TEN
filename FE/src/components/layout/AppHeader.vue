<script setup>
import { useRouter } from 'vue-router'

// Figma Header(68:2) — 56px, 흰 배경, 하단 1px 선. 뒤로가기 + 제목 + 우측 액션.
// 제목 자리에는 slot 으로 로고/아바타를 넣을 수 있다(대시보드·대화 헤더).
const props = defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: false },
  // 'bold' 22px Bold (소비내역·내 정보) / 'medium' 18px Medium (온보딩)
  titleStyle: { type: String, default: 'bold' },
})
const router = useRouter()
const emit = defineEmits(['back'])
function onBack() {
  emit('back')
  if (window.history.length > 1) router.back()
  else router.replace({ name: 'home' })
}
</script>

<template>
  <header class="hdr">
    <button v-if="props.back" type="button" class="back" aria-label="뒤로" @click="onBack">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M15 5l-7 7 7 7" stroke="#1c1917" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div class="title" :class="props.titleStyle">
      <slot name="title">{{ props.title }}</slot>
    </div>
    <div class="right"><slot name="right" /></div>
  </header>
</template>

<style scoped>
.hdr {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  height: var(--header-h);
  padding: 0 16px 0 20px;
  background: var(--surface-card);
  border-bottom: 1px solid var(--border-soft);
}
.hdr:has(.back) {
  padding-left: 16px;
}
.back {
  display: flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  margin-left: -4px;
}
.title {
  flex: 1 0 0;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--gray-900);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.title.bold {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.45;
}
.title.medium {
  font-size: 18px;
  font-weight: 500;
}
.right {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--gray-700);
  font-size: 16px;
  font-weight: 500;
}
</style>
