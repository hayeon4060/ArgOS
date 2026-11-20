<!--
  로그인 카드 위쪽 오류 상자 (로그인 실패, 서버 연결 실패 등)
  - 사용 예: <LoginAlert icon="wifioff" title="서버에 연결하지 못했습니다" text="..." />
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  icon: { type: String, default: 'alert' },
  title: { type: String, required: true },
  text: { type: String, default: '' },
})
</script>

<template>
  <div class="login-alert" role="alert">
    <AppIcon :name="icon" :size="17" />
    <div>
      <b>{{ title }}</b>
      {{ text }}
    </div>
  </div>
</template>

<style scoped>
.login-alert {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding: 11px 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  color: #7f1d1d;
  line-height: 19px;
  animation: softin 0.2s both;
}

.app-icon {
  margin-top: 1px;
  color: var(--color-level-alarm);
}

b {
  display: block;
  font-size: 13.5px;
  color: var(--color-level-alarm-text);
}
</style>
