<!--
  로그인 버튼 (누르는 중이면 회전 표시 + '로그인 중')
-->
<script setup>
defineProps({
  loading: { type: Boolean, default: false },
})
</script>

<template>
  <button class="login-submit" type="submit" :class="{ busy: loading }" :disabled="loading">
    <span v-if="loading" class="spinner" />
    {{ loading ? '로그인 중' : '로그인' }}
  </button>
</template>

<style scoped>
.login-submit {
  width: 100%;
  height: 48px;
  margin-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}

.login-submit:hover {
  background: var(--color-primary-hover);
}

.busy {
  background: #3b82f6;
  cursor: default;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
</style>
