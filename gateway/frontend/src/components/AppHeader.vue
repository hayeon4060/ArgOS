<!--
  상단 바 (화면설계안 js/shell.js buildHeader, css/base.css .hdr 기준)
  - 왼쪽: 로고(누르면 권한별 첫 화면), 제목(title 이 있을 때. 예: 사업장 선택)
  - 가운데: 기본 슬롯 (공장 화면의 메뉴 등을 넣을 자리) + 알림 상태 띠(AlertPill, 주의·경보가 있을 때만)
  - 오른쪽: 체크리스트 진행(HeaderChecklistButton, 진행 중일 때만 · 제목만 있는 화면에서는 숨김),
    수신 상태·시계(AppHeaderStatus), 로그인 유지 시간·연장(SessionTimer),
    채팅·알림 숫자(AppHeaderBadges), 사용자 메뉴(AppHeaderUser)
-->
<script setup>
import { useRouter } from 'vue-router'
import AppHeaderBadges from './AppHeaderBadges.vue'
import AppHeaderStatus from './AppHeaderStatus.vue'
import AppHeaderUser from './AppHeaderUser.vue'
import AppLogo from './AppLogo.vue'
import AlertPill from '@/features/alert/AlertPill.vue'
import HeaderChecklistButton from '@/features/alert/HeaderChecklistButton.vue'
import SessionTimer from '@/features/session/SessionTimer.vue'
import { homeRouteOf } from '@/router/home'
import { useAuthStore } from '@/stores/auth'

defineProps({
  /** 로고 옆 제목 (메뉴가 없는 화면에서 사용) */
  title: { type: String, default: '' },
})

const router = useRouter()
const auth = useAuthStore()

function goHome() {
  router.push(homeRouteOf(auth.user))
}
</script>

<template>
  <header class="app-header">
    <button type="button" class="logo" aria-label="첫 화면으로" @click="goHome">
      <AppLogo />
    </button>
    <div v-if="title" class="title">{{ title }}</div>

    <slot />
    <AlertPill />

    <div class="right">
      <HeaderChecklistButton v-if="!title" />
      <AppHeaderStatus />
      <SessionTimer />
      <AppHeaderBadges />
      <AppHeaderUser />
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.logo {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.title {
  margin-left: 14px;
  padding-left: 18px;
  border-left: 1px solid var(--color-border);
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

/* 1920 보다 좁은 화면: 간격을 줄여서 오른쪽이 화면 밖으로 밀리지 않게 */
@media (max-width: 1800px) {
  .right {
    gap: 12px;
  }
}

@media (max-width: 1600px) {
  .app-header {
    gap: 10px;
    padding: 0 16px;
  }
}
</style>
