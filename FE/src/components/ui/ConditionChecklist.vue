<script setup>
import { ref, computed } from 'vue'
import { useLocaleStore } from '../../stores/locale'

// 우대조건은 하나씩 누르는 것이 아니라 여러 개를 골라 한 번에 보낸다 (UI 흐름 v5 [우대금리 조건 확인]).
// 서버가 action="RATE_CONDITION" 으로 내려주면 버튼 대신 이 체크박스로 그린다.
const props = defineProps({
  items: { type: Array, required: true }, // [{ value, labelKo, labelLocal, nameKo }]
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['submit'])

const locale = useLocaleStore()
const t = (k, v) => locale.t(k, v)
const picked = ref([])

const label = (s) => (locale.isKorean ? s.labelKo : s.labelLocal || s.labelKo)

function toggle(value) {
  const i = picked.value.indexOf(value)
  if (i === -1) picked.value.push(value)
  else picked.value.splice(i, 1)
}

/*
 * 고른 것을 한국어 짧은 이름으로 보낸다. 챗봇이 받은 조건 목록도 한국어라 그대로 맞춰야 코드로 옮긴다.
 * 화면에 보이는 문구(labelKo)는 "앞으로 급여를 ... 받으실 예정인가요?" 라는 질문이라 그대로 옮기면
 * 말풍선이 문단이 된다. 그래서 서버가 함께 준 nameKo("급여이체")를 쓴다.
 *
 * 순서는 고른 순서가 아니라 목록에 보이는 순서다. 같은 항목을 골랐으면 늘 같은 문장이 나온다.
 */
const objectParticle = (word) => {
  const last = word.charCodeAt(word.length - 1)
  // 한글 음절이 아니면(영문·기호로 끝나면) 받침을 알 수 없다. 그때는 '를' 로 둔다.
  if (last < 0xac00 || last > 0xd7a3) return '를'
  return (last - 0xac00) % 28 === 0 ? '를' : '을'
}

const message = computed(() => {
  const names = props.items.filter((s) => picked.value.includes(s.value)).map((s) => s.nameKo || s.labelKo)
  if (!names.length) return '해당하는 것이 없어요. 이대로 제출할게요.'
  const joined = names.join(', ')
  return `${joined}${objectParticle(joined)} 선택할게요`
})
</script>

<template>
  <div class="checks">
    <button
      v-for="s in items"
      :key="s.value"
      type="button"
      class="row"
      :class="{ on: picked.includes(s.value) }"
      :aria-pressed="picked.includes(s.value)"
      :disabled="disabled"
      @click="toggle(s.value)"
    >
      <span class="box" aria-hidden="true">
        <svg v-if="picked.includes(s.value)" width="12" height="12" viewBox="0 0 24 24" fill="none">
          <path d="M5 13l4 4L19 7" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </span>
      <span class="lb">{{ label(s) }}</span>
    </button>

    <button type="button" class="done" :disabled="disabled" @click="emit('submit', message)">
      {{ t('chat.select_done') }}
    </button>
  </div>
</template>

<style scoped>
.checks {
  /* .chat 이 자식 사이에 18px 을 준다. 이 목록은 바로 위 답변에 딸린 것이라 그만큼 벌어지면 안 된다. */
  margin-top: -10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 0 2px 40px;
}
.row {
  display: flex;
  /* 질문 문구가 두 줄을 넘으면 가운데 맞춤이 어긋나 보인다. 체크박스를 첫 줄에 맞춘다. */
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--r-button);
  background: var(--surface-card);
  font-size: 14px;
  color: var(--gray-900);
  text-align: left;
}
.row.on {
  border-color: var(--surface-primary);
  background: var(--yellow-50);
}
.row:disabled {
  opacity: 0.5;
}
.box {
  flex: 0 0 auto;
  margin-top: 1px;
  display: flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--border-strong);
  border-radius: 5px;
  color: var(--on-primary);
}
.row.on .box {
  border-color: var(--surface-primary);
  background: var(--surface-primary);
}
.lb {
  min-width: 0;
  line-height: 1.45;
}
.done {
  margin-top: 2px;
  padding: 11px 12px;
  border-radius: var(--r-button);
  background: var(--surface-primary);
  color: var(--on-primary);
  font-size: 15px;
  font-weight: 500;
}
.done:disabled {
  opacity: 0.5;
}
</style>
