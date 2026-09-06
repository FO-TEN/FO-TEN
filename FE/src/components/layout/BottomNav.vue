<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useLocaleStore } from '../../stores/locale'
import homeOn from '../../assets/icons/nav_home_on.svg'
import homeOff from '../../assets/icons/nav_home_off.svg'
import chatOn from '../../assets/icons/nav_chat_on.svg'
import chatOff from '../../assets/icons/nav_chat_off.svg'
import meOn from '../../assets/icons/nav_me_on.svg'
import meOff from '../../assets/icons/nav_me_off.svg'

// Figma BottomNav(68:6) — 3탭 고정. 활성 gray/900 Medium, 비활성 gray/400 Regular.
// 디자인은 76px 이지만 아이콘·글자가 아래로 쏠려 보여 62px 로 줄이고 위아래 가운데에 맞췄다.
const route = useRoute()
const router = useRouter()
const locale = useLocaleStore()

const tabs = [
  { key: 'home', to: { name: 'home' }, on: homeOn, off: homeOff, label: 'nav.home' },
  { key: 'chat', to: { name: 'chat' }, on: chatOn, off: chatOff, label: 'nav.chat' },
  { key: 'me', to: { name: 'me' }, on: meOn, off: meOff, label: 'nav.me' },
]
const active = (t) => route.meta.nav === t.key
</script>

<template>
  <nav class="nav">
    <button
      v-for="t in tabs"
      :key="t.key"
      type="button"
      class="tab"
      :class="{ active: active(t) }"
      :aria-current="active(t) ? 'page' : undefined"
      @click="router.push(t.to)"
    >
      <img :src="active(t) ? t.on : t.off" alt="" width="24" height="24" />
      <span>{{ locale.t(t.label) }}</span>
    </button>
  </nav>
</template>

<style scoped>
.nav {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  height: var(--nav-h);
  background: var(--surface-card);
  border-top: 1px solid var(--border-soft);
  padding-bottom: env(safe-area-inset-bottom);
}
.tab {
  flex: 1 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  line-height: 1.25;
  color: var(--gray-400);
}
.tab img {
  width: 24px;
  height: 24px;
}
.tab.active {
  color: var(--gray-900);
  font-weight: 500;
}
</style>
