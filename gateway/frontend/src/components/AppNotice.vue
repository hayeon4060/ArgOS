<!--
  짧은 안내 문구 (화면 아래 가운데에 잠깐 떴다 사라짐)
  - 표시·숨김 시간은 utils/useNotice.js 가 관리, 이 컴포넌트는 모양만 담당
  - 사용 예: <AppNotice :message="notice" />
-->
<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  /** 보여 줄 문구 (빈 문자열이면 숨김) */
  message: { type: String, default: '' },
})
</script>

<template>
  <p v-if="message" class="app-notice" role="status">
    <AppIcon name="info" :size="16" />{{ message }}
  </p>
</template>

<style scoped>
.app-notice {
  position: fixed;
  left: 50%;
  bottom: 32px;
  z-index: 20;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: var(--color-text);
  color: #fff;
  font-size: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.2);
  animation: pop-in var(--m-pop-in) both; /* 4px 위에서 내려오며 나타남 (motion.css) */
}
</style>
