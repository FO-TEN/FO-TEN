<script setup>
import food from '../../assets/icons/cat_food.svg'
import coffee from '../../assets/icons/cat_coffee.svg'
import bag from '../../assets/icons/cat_bag.svg'

/*
 * 소비 카테고리 아이콘 (36px 원 + 18px 그림). Figma 217:3104~3123 스타일.
 * 백엔드 카테고리: 식비 · 교통 · 통신 · 쇼핑 · 기타 · 주거
 * 식비/쇼핑/외식은 시안 에셋. 교통·통신·기타·주거는 시안에 없어 임시 인라인 아이콘 — 디자이너가 그리면 교체.
 */
const props = defineProps({ category: { type: String, required: true } })

const map = {
  식비: { bg: 'var(--cat-food)', img: food },
  '외식 · 카페': { bg: 'var(--cat-coffee)', img: coffee },
  외식: { bg: 'var(--cat-coffee)', img: coffee },
  쇼핑: { bg: 'var(--cat-bag)', img: bag },
  교통: { bg: '#4f8ef7', glyph: 'bus' },
  통신: { bg: '#2bb0a5', glyph: 'phone' },
  주거: { bg: '#c17a54', glyph: 'house' },
  기타: { bg: '#a8a29e', glyph: 'dots' },
}
const info = map[props.category] || map['기타']
</script>

<template>
  <span class="ico" :style="{ background: info.bg }">
    <img v-if="info.img" :src="info.img" alt="" width="18" height="18" />
    <svg v-else-if="info.glyph === 'bus'" width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      <rect x="3" y="2.5" width="12" height="11" rx="2.5" stroke="#fff" stroke-width="1.6" />
      <path d="M3 8.5h12" stroke="#fff" stroke-width="1.6" />
      <circle cx="6" cy="11" r="1" fill="#fff" />
      <circle cx="12" cy="11" r="1" fill="#fff" />
      <path d="M5.5 13.5v2M12.5 13.5v2" stroke="#fff" stroke-width="1.6" stroke-linecap="round" />
    </svg>
    <svg v-else-if="info.glyph === 'phone'" width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      <rect x="5" y="2" width="8" height="14" rx="2" stroke="#fff" stroke-width="1.6" />
      <circle cx="9" cy="13" r="0.9" fill="#fff" />
    </svg>
    <svg v-else-if="info.glyph === 'house'" width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      <path d="M3 8.5L9 3.5L15 8.5" stroke="#fff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M4.5 7.5V14.5H13.5V7.5" stroke="#fff" stroke-width="1.6" stroke-linejoin="round" />
      <rect x="7.5" y="10.5" width="3" height="4" stroke="#fff" stroke-width="1.4" />
    </svg>
    <svg v-else width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      <circle cx="4" cy="9" r="1.6" fill="#fff" />
      <circle cx="9" cy="9" r="1.6" fill="#fff" />
      <circle cx="14" cy="9" r="1.6" fill="#fff" />
    </svg>
  </span>
</template>

<style scoped>
.ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 50%;
}
</style>
