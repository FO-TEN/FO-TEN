<script setup>
import { ref, computed } from 'vue'
import { LANGUAGES } from '../../i18n'
import chevron from '../../assets/icons/chevron_down.svg'
import check from '../../assets/icons/check.svg'

// Figma LangToggle(97:33) — 알약형 트리거 + 언어 메뉴(현재 언어 옐로 배경 + 체크).
// 디자인은 9개였지만 백엔드가 18개 언어를 지원하므로 18개 전부 보여준다(규칙 9).
const props = defineProps({
  modelValue: { type: String, required: true }, // 언어 코드
  align: { type: String, default: 'right' }, // 메뉴가 트리거 기준 어느 쪽으로 열리나
})
const emit = defineEmits(['update:modelValue'])
const open = ref(false)
const current = computed(() => LANGUAGES.find((l) => l.code === props.modelValue) || LANGUAGES[0])

function pick(code) {
  emit('update:modelValue', code)
  open.value = false
}
</script>

<template>
  <div class="wrap" :class="[align, { open }]">
    <button type="button" class="pill" :aria-expanded="open" @click="open = !open">
      <span>{{ current.label }}</span>
      <img :src="chevron" alt="" width="12" height="12" class="chev" />
    </button>

    <teleport to="body">
      <div v-if="open" class="dim" @click="open = false" />
    </teleport>

    <ul v-if="open" class="menu" role="listbox">
      <li
        v-for="l in LANGUAGES"
        :key="l.code"
        role="option"
        :aria-selected="l.code === props.modelValue"
        class="item"
        :class="{ on: l.code === props.modelValue }"
        @click="pick(l.code)"
      >
        <span>{{ l.label }}</span>
        <img v-if="l.code === props.modelValue" :src="check" alt="" width="16" height="16" />
      </li>
    </ul>
  </div>
</template>

<style scoped>
.wrap {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  gap: 6px;
  z-index: 30;
}
.wrap.right {
  align-items: flex-end;
}
.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px 8px 14px;
  border-radius: var(--r-pill);
  background: var(--gray-100);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--gray-900);
}
.open .pill {
  background: var(--surface-card);
}
.chev {
  transition: transform 0.15s;
}
.open .chev {
  transform: rotate(180deg);
}
.dim {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: rgba(28, 26, 23, 0.2);
}
.menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  width: 196px;
  max-height: 60vh;
  overflow-y: auto;
  margin: 0;
  padding: 6px;
  list-style: none;
  background: var(--surface-card);
  border: 1px solid var(--border-soft);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-menu);
}
.wrap.left .menu {
  right: auto;
  left: 0;
}
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 10px 10px 12px;
  border-radius: var(--r-input);
  font-size: 16px;
  line-height: 1.4;
  color: var(--gray-900);
  cursor: pointer;
}
.item.on {
  background: var(--yellow-50);
  font-weight: 500;
}
</style>
