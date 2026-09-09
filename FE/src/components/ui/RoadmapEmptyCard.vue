<script setup>
import { useLocaleStore } from '../../stores/locale'
import PotenAvatar from './PotenAvatar.vue'
import chev from '../../assets/icons/chevron_right.svg'

// 홈 저축 로드맵 · 로드맵이 없을 때 (Figma 04d-0, #121). 누르면 'create'
const emit = defineEmits(['create'])
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)
</script>

<template>
  <section class="empty">
    <p class="title">{{ t('home.roadmap_empty_title') }}</p>
    <p class="sub">{{ t('home.roadmap_empty_sub') }}</p>

    <div class="figure">
      <span class="ring" />
      <PotenAvatar :size="100" mood="wink" class="pot" />
    </div>

    <button type="button" class="cta" @click="emit('create')">
      <span>{{ t('home.roadmap_create') }}</span>
      <img :src="chev" alt="" width="16" height="16" />
    </button>
  </section>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 20px 20px;
  border-radius: var(--r-card-lg);
  background: var(--surface-card);
  text-align: center;
}
.title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--gray-850);
}
.sub {
  font-size: 14px;
  line-height: 1.5;
  color: var(--gray-500);
}
/* 연한 원 위에 캐릭터 */
.figure {
  position: relative;
  width: 120px;
  height: 110px;
  margin: 10px 0 14px;
}
.ring {
  position: absolute;
  left: 4px;
  top: 6px;
  width: 112px;
  height: 112px;
  border-radius: 50%;
  background: #fff6d6;
}
.pot {
  position: absolute;
  left: 10px;
  top: 22px;
  width: 100px !important;
  height: 79px !important;
}
.cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 15px 16px 15px 18px;
  border-radius: 14px;
  background: var(--surface-primary);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--gray-850);
}
.cta:active {
  transform: scale(0.99);
}
</style>
