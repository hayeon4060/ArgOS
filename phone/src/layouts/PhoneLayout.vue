<!--
  담당자 앱 레이아웃: 위 바(제목·이름·로그아웃) + 본문 + 아래 탭(공정 현황 / 체크리스트) + 알림 띠
  - 휴대폰 폭 기준. PC 브라우저에서 열면 가운데 480px 로 보임
  - 600px 이상(폴드 펼친 화면·태블릿): 아래 탭 → 왼쪽 레일, 체크리스트는 목록 | 한 건 나란히 (1 : 2, 체크리스트를 크게)
  - 내 체크리스트를 처음 한 번 + 데이터가 바뀔 때마다 다시 받고(stores/myChecklists),
    새 체크리스트나 '다시 알림'이 오면 위에서 알림 띠(PushBanner) + 진동
  - 브라우저로 열었을 때(시연): 위 바 아래 '알림 켜기' 줄 → 휴대폰 화면이 꺼져 있어도 알림 (webPush.js)
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
import ChecklistListPage from '../pages/ChecklistListPage.vue'
import { startPush, stopPush } from '../push'
import { enableWebPush, initWebPush, stopWebPush, webPush } from '../webPush'
import { useMyChecklistsStore } from '../stores/myChecklists'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const my = useMyChecklistsStore()

onMounted(() => {
  my.refresh()
  markPhoneUser(auth.user.id, true)
  // 휴대폰 알림(FCM): 누르면 그 체크리스트로, 앱이 켜져 있을 때 오면 다시 받아서 알림 띠
  startPush({ userId: auth.user.id, onOpen: openPush, onReceive: my.refresh }).catch((e) => console.warn('[push]', e))
  initWebPush(auth.user.id) // 브라우저(시연): 이미 허용했으면 다시 등록, 아니면 '알림 켜기' 줄
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

/* ───────── 넓은 화면 (폴드 펼침) ───────── */
const wideMq = window.matchMedia('(min-width: 600px)')
const wide = ref(wideMq.matches)
const onWide = (e) => (wide.value = e.matches)
wideMq.addEventListener('change', onWide) // 접고 펼 때마다 바뀜 (앱은 다시 시작하지 않음, AndroidManifest configChanges)
onBeforeUnmount(() => wideMq.removeEventListener('change', onWide))
const split = computed(() => wide.value && route.meta.tab === 'checklists')

/* ───────── 위 바 · 아래 탭 ───────── */
const title = computed(() => route.meta.title ?? 'ArgOS')
const origin = location.origin

async function onLogout() {
  markPhoneUser(auth.user.id, false)
  await stopPush() // 로그인 토큰이 있을 때 먼저 토큰 지우기
  await stopWebPush()
  await auth.logout()
  my.reset()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="top">
      <button v-if="route.meta.back && !split" type="button" class="icon-btn back" aria-label="뒤로" @click="router.push({ name: route.meta.back })">
        <AppIcon name="cl" :size="22" />
      </button>
      <span v-else class="mark"><AppIcon name="eye" :size="18" /></span>
      <h1>{{ title }}</h1>
      <span class="me">{{ auth.user?.name }}</span>
      <button type="button" class="icon-btn" aria-label="로그아웃" @click="onLogout"><AppIcon name="logout" :size="19" /></button>
    </header>

    <!-- 휴대폰 알림 켜기 (브라우저 시연, webPush.js) -->
    <p v-if="webPush && webPush !== 'on'" class="push-strip" :class="webPush">
      <AppIcon name="bell" :size="16" />
      <span v-if="webPush === 'off'">화면이 꺼져 있어도 체크리스트 알림을 받으려면</span>
      <span v-else-if="webPush === 'denied'">알림이 막혀 있습니다. 주소창 왼쪽 사이트 설정 → 알림 → 허용으로 바꿔 주세요.</span>
      <span v-else>
        이 주소(http)에서는 크롬이 알림을 막습니다. 크롬 주소창에 <b class="mono">chrome://flags</b> →
        'Insecure origins treated as secure' 에 <b class="mono">{{ origin }}</b> 추가 후 다시 시작
      </span>
      <button v-if="webPush === 'off'" type="button" class="btn btn-primary" @click="enableWebPush(auth.user.id)">알림 켜기</button>
    </p>

    <main class="main" :class="{ split }">
      <template v-if="split">
        <ChecklistListPage class="pane-list" />
        <div class="pane-detail">
          <RouterView v-if="route.name === 'checklist'" />
          <p v-else class="pane-empty"><AppIcon name="clip" :size="28" />왼쪽에서 체크리스트를 고르세요.</p>
        </div>
      </template>
      <RouterView v-else />
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

.push-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 8px 16px;
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-size: 13px;
  line-height: 19px;
  word-break: keep-all;
}

.push-strip span {
  flex: 1;
}

.push-strip.denied,
.push-strip.insecure {
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
}

.push-strip .btn {
  flex: none;
  height: 34px;
  padding: 0 12px;
  font-size: 13px;
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

/* ───────── 600px 이상: 폴드 펼친 화면 ───────── */
@media (min-width: 600px) {
  .shell {
    max-width: none;
    display: grid;
    grid-template-columns: 88px 1fr;
    grid-template-rows: auto auto 1fr; /* 위 바 · 알림 켜기 줄(없으면 0) · 본문 */
    box-shadow: none;
  }

  .top {
    grid-column: 1 / -1;
    padding-left: max(16px, env(safe-area-inset-left));
    padding-right: max(8px, env(safe-area-inset-right));
  }

  .push-strip {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .main {
    grid-column: 2;
    grid-row: 3;
    width: 100%;
    max-width: 1200px;
    padding: 20px 24px 24px;
  }

  .tabs {
    grid-column: 1;
    grid-row: 3;
    position: sticky;
    top: calc(56px + env(safe-area-inset-top));
    align-self: start;
    height: calc(100dvh - 56px - env(safe-area-inset-top));
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px 8px env(safe-area-inset-bottom) max(8px, env(safe-area-inset-left));
    border-top: none;
    border-right: 1px solid var(--color-border);
  }

  .tab {
    height: 68px;
    border-radius: 12px;
  }

  .tab.on {
    background: var(--color-primary-soft);
  }

  /* 목록 1 : 체크리스트 2 (목록은 200px 아래로는 안 줄임) — 체크리스트를 크게 */
  .main.split {
    display: grid;
    grid-template-columns: minmax(200px, 1fr) minmax(0, 2fr);
    gap: 16px;
    align-items: start;
  }

  /* 좁아진 목록: 고른 줄은 파란 테두리로 보이니 꺾쇠는 빼고 여백을 줄임 */
  .pane-list :deep(.row) {
    gap: 10px;
    padding: 12px;
  }

  .pane-list :deep(.chev) {
    display: none;
  }

  .pane-list {
    position: sticky;
    top: calc(76px + env(safe-area-inset-top));
    max-height: calc(100dvh - 96px - env(safe-area-inset-top));
    overflow-y: auto;
  }

  .pane-detail {
    min-width: 0;
  }

  .pane-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    margin-top: 80px;
    color: var(--color-text-weak);
    font-size: 14px;
  }

  :global(.toasts) {
    bottom: calc(16px + env(safe-area-inset-bottom)) !important;
  }
}
</style>
