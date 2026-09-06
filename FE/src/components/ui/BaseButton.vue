<script setup>
// Figma Button/Primary/Large(68:16) 옐로+브라운 · Button/Secondary/Large(68:20) 흰+선 · Button/Text(68:24)
defineProps({
  variant: { type: String, default: 'primary' }, // primary | secondary | text
  type: { type: String, default: 'button' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})
</script>

<template>
  <button :type="type" class="btn" :class="[variant, { loading }]" :disabled="disabled || loading">
    <span v-if="loading" class="spinner" aria-hidden="true" />
    <span class="label"><slot /></span>
  </button>
</template>

<style scoped>
.btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 52px;
  padding: 0 20px;
  border-radius: var(--r-button);
  font-size: 17px;
  font-weight: 500;
  white-space: nowrap;
  transition: opacity 0.15s, transform 0.05s;
}
.btn:active:not(:disabled) {
  transform: scale(0.99);
}
.btn:disabled {
  opacity: 0.45;
  cursor: default;
}
.primary {
  background: var(--surface-primary);
  color: var(--on-primary);
}
.secondary {
  background: var(--surface-card);
  color: var(--gray-900);
  border: 1px solid var(--border-strong);
}
.text {
  height: 40px;
  background: transparent;
  color: var(--gray-700);
  font-size: 16px;
}
.spinner {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
