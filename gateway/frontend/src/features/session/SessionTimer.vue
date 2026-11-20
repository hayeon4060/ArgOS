<!--
  상단 바의 로그인 유지 시간 표시 + '연장' 버튼
  - 남은 시간을 시:분:초로 표시, 만료 10분 전부터 주황 글자
  - '연장' → 만료 시각이 1시간 늘어남 (횟수 제한 없음 — 가정)
  - 만료 10분 전에 한 번 안내 문구, 만료되면 자동 로그아웃 (useSessionTimer)
  - 설정 값은 sessionConfig.js 한 곳
-->
<script setup>
import { ref } from 'vue'
import { EXTEND_HOURS } from './sessionConfig'
import { useSessionTimer } from './useSessionTimer'
import AppIcon from '@/components/AppIcon.vue'
import AppNotice from '@/components/AppNotice.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDuration } from '@/utils/format'
import { useNotice } from '@/utils/useNotice'

const auth = useAuthStore()

// 안내 문구 (조금 길게 6초)
const { message: noticeMessage, show: showNotice } = useNotice(6000)

const { remainingMs, isNearExpiry } = useSessionTimer({
  onWarn: () =>
    showNotice(`로그인 유지 시간이 곧 끝납니다. 상단의 '연장'을 누르면 ${EXTEND_HOURS}시간 늘어납니다.`),
})

const extending = ref(false)

async function onExtend() {
  extending.value = true
  try {
    await auth.extend()
    showNotice(`로그인 유지 시간을 ${EXTEND_HOURS}시간 연장했습니다.`)
  } catch (error) {
    showNotice(error.message)
  } finally {
    extending.value = false
  }
}
</script>

<template>
  <div class="session-timer" :class="{ near: isNearExpiry }" title="로그인 유지 남은 시간">
    <AppIcon name="clock" :size="15" />
    <span class="mono">{{ formatDuration(remainingMs) }}</span>
    <button type="button" class="extend" :disabled="extending" @click="onExtend">연장</button>
  </div>
  <AppNotice :message="noticeMessage" />
</template>

<style scoped>
.session-timer {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-sub);
}

.session-timer .app-icon {
  color: var(--color-text-weak);
}

/* 만료가 가까우면 주황 */
.near,
.near .app-icon {
  color: var(--color-level-warn-text);
}

.extend {
  height: 26px;
  padding: 0 9px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
}

.extend:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.extend:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
