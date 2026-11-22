<!--
  상단 메뉴 (화면설계안 js/shell.js buildHeader 의 nav)
  - 탭 4개 + '관리' 펼침(사용자 관리, 공장 관리)
  - 선택 표시: 지금 화면의 meta.nav 와 같은 탭에 파란 글자 + 아래 밑줄
  - '관리' 펼침은 바깥을 누르거나 Esc 를 누르면 닫힘 (utils/useOutsideClose.js), 열고 닫힐 때 menu-drop 움직임 (motion.css)
  - 메뉴 목록은 router/menus.js 한 곳에서 관리
  - 아직 설정하지 않은 사업장(공장 0개, features/site/siteSetup.js)은 탭을 숨기고 '관리'만 (2026-11-20 재원님 요청)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { checkSite, isEmptySite } from '@/features/site/siteSetup'
import { NAV_ADMIN, NAV_TABS } from '@/router/menus'
import { useOutsideClose } from '@/utils/useOutsideClose'

const route = useRoute()

// 지금 사업장 ID (/sites/:siteId/...) — 메뉴 주소에 그대로 붙임
const siteId = computed(() => route.params.siteId)
const currentNav = computed(() => route.meta.nav)

// 새로 추가해 아직 공장이 없는 사업장: 대시보드 · 통계 · 점검 탭은 볼 것이 없어서 숨김
watch(siteId, checkSite, { immediate: true })
const emptySite = computed(() => isEmptySite(siteId.value))

// '관리' 펼침 열림 여부 (다른 화면으로 가면 닫음)
const adminOpen = ref(false)
watch(() => route.fullPath, () => (adminOpen.value = false))
const adminRoot = ref(null)
useOutsideClose(adminRoot, adminOpen)
</script>

<template>
  <nav class="app-nav">
    <RouterLink
      v-for="tab in emptySite ? [] : NAV_TABS"
      :key="tab.nav"
      :to="{ name: tab.name, params: { siteId } }"
      class="tab"
      :class="{ on: currentNav === tab.nav }"
    >
      <AppIcon :name="tab.icon" />{{ tab.label }}
    </RouterLink>

    <div ref="adminRoot" class="admin">
      <button
        type="button"
        class="tab"
        :class="{ on: currentNav === 'admin' }"
        :aria-expanded="adminOpen"
        @click="adminOpen = !adminOpen"
      >
        <AppIcon name="gear" />관리<AppIcon name="cd" :size="14" class="chev" />
      </button>

      <Transition name="menu-drop">
        <div v-if="adminOpen" class="menu" role="menu">
          <RouterLink
            v-for="item in NAV_ADMIN"
            :key="item.name"
            :to="{ name: item.name, params: { siteId } }"
            class="menu-item"
            role="menuitem"
            @click="adminOpen = false"
          >
            <AppIcon :name="item.icon" :size="16" />
            <span>
              <b>{{ item.label }}</b>
              <small v-if="item.sub">{{ item.sub }}</small>
            </span>
          </RouterLink>
        </div>
      </Transition>
    </div>
  </nav>
</template>

<style scoped>
.app-nav {
  display: flex;
  height: var(--topbar-height);
  margin-left: 8px;
}

.tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 7px;
  height: 100%;
  padding: 0 15px;
  border: none;
  background: none;
  color: var(--color-text-sub);
  font-size: 15px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s;
}

.tab:hover {
  color: var(--color-text);
}

/* 선택된 탭: 파란 글자 + 아래 밑줄 */
.tab.on {
  color: var(--color-primary);
  font-weight: 700;
}

.tab.on::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--color-primary);
  animation: tab-line var(--m-tab) both; /* 선택하면 밑줄이 가운데서 펼쳐짐 */
}

.chev {
  color: var(--color-text-weak);
}

@keyframes tab-line {
  from {
    transform: scaleX(0.3);
    opacity: 0;
  }
}

/* '관리' 펼침 */
.admin {
  position: relative;
}

.menu {
  position: absolute;
  left: 0;
  top: calc(100% - 6px);
  z-index: 20;
  width: 230px;
  padding: 6px;
  background: #fff;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  color: var(--color-text);
  text-decoration: none;
}

.menu-item:hover {
  background: var(--color-surface-sub);
}

.menu-item .app-icon {
  color: var(--color-text-sub);
}

.menu-item b {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.menu-item small {
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 1920 보다 좁은 화면: 탭 여백을 줄이고 아이콘은 숨김 (글자만) */
@media (max-width: 1600px) {
  .app-nav {
    margin-left: 0;
  }

  .app-nav > .tab,
  .admin > .tab {
    padding: 0 10px;
  }

  .app-nav > .tab > .app-icon,
  .admin > .tab > .app-icon:first-child {
    display: none;
  }
}
</style>
