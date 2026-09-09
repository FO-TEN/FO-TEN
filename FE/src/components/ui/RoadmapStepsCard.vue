<script setup>
import { useLocaleStore } from '../../stores/locale'

// 로드맵 소개 턴의 3단계 카드 (UI 흐름 v5). 번호 배지 + 한 줄씩
const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)
const steps = ['chat.intro_step_1', 'chat.intro_step_2', 'chat.intro_step_3']
</script>

<template>
  <section class="steps">
    <div v-for="(k, i) in steps" :key="k" class="step">
      <div class="rail">
        <span class="no num">{{ i + 1 }}</span>
        <span v-if="i < steps.length - 1" class="line" />
      </div>
      <p class="txt">{{ t(k) }}</p>
    </div>
  </section>
</template>

<style scoped>
.steps {
  display: flex;
  flex-direction: column;
  margin-top: 8px;
  padding: 12px 16px 4px;
  border-radius: var(--r-card);
  background: var(--surface-card);
}
.step {
  display: flex;
  gap: 12px;
}
.rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 0 0 auto;
  width: 24px;
}
.no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--surface-primary);
  font-size: 12px;
  font-weight: 700;
  color: var(--gray-850);
}
.line {
  flex: 1 0 12px;
  width: 2px;
  margin: 4px 0;
  border-radius: 1px;
  background: var(--gray-200);
}
.txt {
  flex: 1 1 0;
  min-width: 0;
  padding: 2px 0 10px;
  font-size: 14px;
  line-height: 1.45;
  color: var(--gray-900);
  word-break: keep-all;
}
.step:last-child .txt {
  padding-bottom: 6px;
}
.line {
  flex: 1 0 8px;
}
</style>
