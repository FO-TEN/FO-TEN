<script setup>
import { computed } from 'vue'

/*
 * Figma Input(68:26) — 라벨 위 · 입력창 · 단위. 라벨은 placeholder 로 대체하지 않는다.
 * type
 *   text / password : 그대로
 *   money           : 숫자만 받고 천 단위 구분자를 보여준다. modelValue 는 숫자(원 단위)
 *   date            : YYYY-MM-DD 문자열. 화면은 브라우저 date 입력을 쓴다
 *   select          : options [{ value, label }]
 */
const props = defineProps({
  label: { type: String, required: true },
  // 'md' 14px(로그인) / 'lg' 16px(회원가입 · 온보딩 — 항목이 많아 라벨을 조금 더 키운다)
  labelSize: { type: String, default: 'md' },
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  options: { type: Array, default: () => [] },
  error: { type: String, default: '' },
  autocomplete: { type: String, default: 'off' },
  maxlength: { type: Number, default: undefined },
})
const emit = defineEmits(['update:modelValue'])

const moneyText = computed(() => {
  const v = props.modelValue
  if (v === '' || v === null || v === undefined) return ''
  return Number(v).toLocaleString('ko-KR')
})
function onMoney(e) {
  const digits = e.target.value.replace(/[^\d]/g, '')
  emit('update:modelValue', digits === '' ? '' : Number(digits))
  // 커서가 뒤로 밀리지 않게 값을 다시 그린다
  e.target.value = digits === '' ? '' : Number(digits).toLocaleString('ko-KR')
}
</script>

<template>
  <label class="input">
    <span class="lbl" :class="labelSize">{{ label }}</span>
    <span class="field" :class="{ err: !!error }">
      <select
        v-if="type === 'select'"
        class="ctl"
        :value="modelValue"
        @change="emit('update:modelValue', $event.target.value)"
      >
        <option v-if="placeholder" value="" disabled>{{ placeholder }}</option>
        <option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      <input
        v-else-if="type === 'money'"
        class="ctl num"
        inputmode="numeric"
        :value="moneyText"
        :placeholder="placeholder"
        @input="onMoney"
      />
      <input
        v-else
        class="ctl"
        :class="{ num: type === 'date' }"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        @input="emit('update:modelValue', $event.target.value)"
      />
      <span v-if="type === 'select'" class="unit">⌄</span>
      <!-- 단위 자리. 기본은 글자 하나지만 slot 으로 토글(원 | ₱)을 끼울 수 있다 -->
      <slot v-else name="unit"><span v-if="unit" class="unit">{{ unit }}</span></slot>
    </span>
    <span v-if="error" class="hint err">{{ error }}</span>
    <span v-else-if="hint" class="hint">{{ hint }}</span>
  </label>
</template>

<style scoped>
.input {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.lbl {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-700);
}
.lbl.lg {
  font-size: 16px;
  font-weight: 700;
}
.field {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: var(--surface-card);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-input);
}
.field:focus-within {
  border-color: var(--gray-700);
}
.field.err {
  border-color: var(--red);
}
.ctl {
  flex: 1 0 0;
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 17px;
  font-weight: 500;
  line-height: 1.3;
  color: var(--gray-900);
  appearance: none;
  -webkit-appearance: none;
}
select.ctl {
  padding-right: 0;
}
input[type='date'].ctl::-webkit-calendar-picker-indicator {
  opacity: 0.5;
}
.unit {
  flex: 0 0 auto;
  font-size: 14px;
  color: var(--gray-400);
}
.hint {
  margin-top: -2px;
  font-size: 14px;
  color: var(--gray-400);
}
.hint.err {
  color: var(--red);
}
</style>
