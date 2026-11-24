<!--
  위쪽 알림 띠 (푸시 알림 자리)
  - 새 체크리스트를 받았거나 공장 담당자가 '다시 알림'을 누르면 위에서 내려옴, 8초 뒤 자동으로 닫힘
  - 누르면 그 체크리스트로 이동
  - 지금은 앱이 켜져 있을 때 데이터 변화로 알아챔 (PhoneLayout). FCM 을 붙이면 앱이 꺼져 있어도 휴대폰 알림으로 옴
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  /** { kind: 'new'|'remind', title, checklistId } 또는 null */
  push: { type: Object, default: null },
})

const emit = defineEmits(['open', 'close'])
</script>

<template>
  <Transition name="push">
    <div v-if="push" class="push" :class="push.kind" role="alert">
      <button type="button" class="body" @click="emit('open', push.checklistId)">
        <span class="icon"><AppIcon name="bell" :size="20" /></span>
        <span class="text">
          <b>{{ push.kind === 'remind' ? '다시 알림 · 체크리스트를 확인해 주세요' : '새 체크리스트가 도착했습니다' }}</b>
          <span>{{ push.title }}</span>
        </span>
      </button>
      <button type="button" class="close" aria-label="알림 닫기" @click="emit('close')"><AppIcon name="x" :size="18" /></button>
    </div>
  </Transition>
</template>

<style scoped>
.push {
  position: fixed;
  top: calc(8px + env(safe-area-inset-top));
  left: 50%;
  z-index: 160;
  width: min(calc(100vw - 16px), 464px);
  transform: translateX(-50%);
  display: flex;
  align-items: stretch;
  border-radius: 14px;
  background: var(--color-text);
  color: #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.35);
}

.body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px 14px 14px;
  border: none;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.icon {
  width: 38px;
  height: 38px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--color-primary);
}

.remind .icon {
  background: var(--color-level-warn);
}

.text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: #cbd5e1;
}

.text b {
  font-size: 14.5px;
  color: #fff;
}

.close {
  width: 44px;
  flex: none;
  border: none;
  background: none;
  color: #94a3b8;
  cursor: pointer;
}

.push-enter-active {
  transition:
    transform 300ms var(--ease-out),
    opacity 300ms;
}

.push-leave-active {
  transition:
    transform 200ms var(--ease-in),
    opacity 200ms;
}

.push-enter-from,
.push-leave-to {
  opacity: 0;
  transform: translate(-50%, -24px);
}
</style>
