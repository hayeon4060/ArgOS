<!--
  담당자 앱 레이아웃: 위 바(제목·이름·로그아웃) + 본문 + 아래 탭(공정 현황 / 체크리스트) + 알림 띠
  - 휴대폰 폭 기준. PC 브라우저에서 열면 가운데 480px 로 보임
  - 내 체크리스트를 처음 한 번 + 데이터가 바뀔 때마다 다시 받고(stores/myChecklists),
    새 체크리스트나 '다시 알림'이 오면 위에서 알림 띠(PushBanner) + 진동
-->
<script setup>
import { Haptics, NotificationType } from '@capacitor/haptics'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppToasts from '@/components/AppToasts.vue'
import { useAuthStore } from '@/stores/auth'
import { useLiveReload } from '@/utils/useLiveReload'
import { markPhoneUser } from '../api/myChecklists'
import PushBanner from '../components/PushBanner.vue'
import { useMyChecklistsStore } from '../stores/myChecklists'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const my = useMyChecklistsStore()

onMounted(() => {
  my.refresh()
  markPhoneUser(auth.user.id, true)
})
useLiveReload(my.refresh)

/* ───────── 새 체크리스트 · 다시 알림 알아채기 ───────── */
const push = ref(null)
let pushTimer = null
let seen = null // 지난번 { 체크리스트 id: 다시 알림 횟수 } (처음 한 번은 비교하지 않음)

function showPush(kind, checklist) {
  push.value = { kind, title: checklist.title, checklistId: checklist.id }
  Haptics.notification({ type: NotificationType.Warning }).catch(() => {}) // 진동 없는 기기·브라우저는 그냥 넘어감
  clearTimeout(pushTimer)
  pushTimer = setTimeout(() => (push.value = null), 8000)
}

watch(
  () => my.reminds,
  (now) => {
    if (seen) {
      for (const c of my.active) {
        if (!(c.id in seen)) showPush('new', c)
        else if (now[c.id] > seen[c.id]) showPush('remind', c)
      }
    }
    seen = { ...now }
  },
)

function openPush(checklistId) {
  push.value = null
  router.push({ name: 'checklist', params: { checklistId } })
}

onBeforeUnmount(() => clearTimeout(pushTimer))

/* ───────── 위 바 · 아래 탭 ───────── */
const title = computed(() => route.meta.title ?? 'ArgOS')

async function onLogout() {
  markPhoneUser(auth.user.id, false)
  await auth.logout()
  my.reset()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="top">
      <button v-if="route.meta.back" type="button" class="icon-btn back" aria-label="뒤로" @click="router.push({ name: route.meta.back })">
        <AppIcon name="cl" :size="22" />
      </button>
      <span v-else class="mark"><AppIcon name="eye" :size="18" /></span>
      <h1>{{ title }}</h1>
      <span class="me">{{ auth.user?.name }}</span>
      <button type="button" class="icon-btn" aria-label="로그아웃" @click="onLogout"><AppIcon name="logout" :size="19" /></button>
    </header>

    <main class="main">
      <RouterView />
    </main>

    <nav class="tabs" aria-label="아래 메뉴">
      <RouterLink :to="{ name: 'home' }" class="tab" :class="{ on: route.meta.tab === 'home' }">
        <AppIcon name="factory" :size="21" />공정 현황
      </RouterLink>
      <RouterLink :to="{ name: 'checklists' }" class="tab" :class="{ on: route.meta.tab === 'checklists' }">
        <span class="tab-icon">
          <AppIcon name="clip" :size="21" />
          <span v-if="my.active.length" class="badge mono">{{ my.active.length }}</span>
        </span>
        체크리스트
      </RouterLink>
    </nav>

    <PushBanner :push="push" @open="openPush" @close="push = null" />
    <AppToasts />
  </div>
</template>

<style scoped>
.shell {
  max-width: 480px;
  min-height: 100dvh;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  box-shadow: 0 0 0 1px var(--color-border);
}

.top {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 8px;
  height: calc(56px + env(safe-area-inset-top));
  padding: env(safe-area-inset-top) 8px 0 16px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.mark {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--color-primary-hover);
  color: #fff;
}

h1 {
  flex: 1;
  font-size: 18px;
  font-weight: 700;
}

.me {
  font-size: 13px;
  color: var(--color-text-sub);
}

.icon-btn {
  width: 44px;
  height: 44px;
  flex: none;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 10px;
  background: none;
  color: var(--color-text-sub);
  cursor: pointer;
}

.icon-btn.back {
  margin-left: -8px;
}

.main {
  flex: 1;
  padding: 16px 16px 24px;
}

.tabs {
  position: sticky;
  bottom: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}

.tab {
  height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-weak);
  text-decoration: none;
}

.tab.on {
  color: var(--color-primary);
}

.tab-icon {
  position: relative;
  display: grid;
}

.badge {
  position: absolute;
  top: -5px;
  right: -11px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-level-warn);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

/* 알림 띠(웹 AppToasts)는 아래 탭 위로, 휴대폰 폭에서는 줄바꿈 */
:global(.toasts) {
  bottom: calc(76px + env(safe-area-inset-bottom)) !important;
}

:global(.toast) {
  max-width: calc(100vw - 32px);
  white-space: normal !important;
  padding-top: 10px !important;
  padding-bottom: 10px !important;
}
</style>
