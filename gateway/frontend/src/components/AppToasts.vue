<!--
  알림 띠 모음 (utils/toast.js 의 toasts 를 그림, 화면설계안 css/extra.css .toast)
  - 화면 아래 가운데, 최근 것이 아래. 나타날 때 아래에서 올라옴(.3s), 사라질 때 살짝 내려가며(.22s)
  - body 로 옮겨 그림(Teleport): 화면 전환 중 확대·축소에 같이 움직이지 않게
-->
<script setup>
import AppIcon from './AppIcon.vue'
import { toasts } from '@/utils/toast'

const ICON = { ok: 'check', warn: 'tri', err: 'alert', info: 'info' }

function onAction(t) {
  t.onAction?.()
  const i = toasts.indexOf(t)
  if (i >= 0) toasts.splice(i, 1)
}
</script>

<template>
  <Teleport to="body">
    <TransitionGroup tag="div" name="toast" class="toasts">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type" :role="t.type === 'err' ? 'alert' : 'status'">
        <AppIcon :name="ICON[t.type]" :size="19" />
        <span>{{ t.message }}</span>
        <button v-if="t.action" type="button" class="action" @click="onAction(t)">{{ t.action }}</button>
      </div>
    </TransitionGroup>
  </Teleport>
</template>

<style scoped>
/* 창(모달) 위, 상황 바꾸기 도구 아래 */
.toasts {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 34px;
  z-index: 150;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 18px 0 14px;
  border-radius: var(--radius-md);
  background: var(--color-text);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.3);
}

.toast.ok .app-icon {
  color: #4ade80;
}

.toast.warn .app-icon {
  color: #fdba74;
}

.toast.err .app-icon {
  color: #fca5a5;
}

.toast.info .app-icon {
  color: #93c5fd;
}

.action {
  margin-left: 10px;
  padding: 0;
  border: none;
  background: none;
  color: #93c5fd;
  font-weight: 700;
  cursor: pointer;
}

.action:hover {
  text-decoration: underline;
}

/* 나타남: 아래 16px 에서 올라옴 / 사라짐: 8px 내려가며 흐려짐 */
.toast-enter-active {
  transition:
    opacity 300ms cubic-bezier(0.2, 0.9, 0.3, 1.1),
    transform 300ms cubic-bezier(0.2, 0.9, 0.3, 1.1);
}

.toast-leave-active {
  transition:
    opacity 220ms ease-in,
    transform 220ms ease-in;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(16px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-from,
  .toast-leave-to {
    transform: none;
  }
}
</style>
