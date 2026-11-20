<!--
  로그인 뒤 화면의 공통 레이아웃: 상단 바 + 본문
  - 상단 바: 로고 + (사업장 이름 SitePill + 메뉴 AppNav) + 오른쪽 상태·사용자 (AppHeader)
    · 사업장 선택처럼 meta.plain 인 화면은 메뉴 없이 제목만 (설계안 'hdr plain')
  - 본문: PageTransition (화면 전환 효과 + RouterView)
  - 사업장 선택 → 사업장 → 공장 → 공정 → 설비가 모두 이 레이아웃이라, 화면이 바뀌어도 상단 바는 그대로 있고 본문만 전환됨
-->
<script setup>
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getSiteName } from '@/api/sites'
import AppHeader from '@/components/AppHeader.vue'
import AppNav from '@/components/AppNav.vue'
import SitePill from '@/components/SitePill.vue'
import PageTransition from '@/features/motion/PageTransition.vue'
import AlertHost from '@/features/alert/AlertHost.vue'
import ChatPanel from '@/features/chat/ChatPanel.vue'
import { USE_MOCK } from '@/api/mock'

// 상황 바꾸기 도구는 개발 모드(npm run dev) + 예시 데이터일 때만 불러옴 → 배포 빌드에서는 코드도 빠짐
const DemoTool = import.meta.env.DEV && USE_MOCK ? defineAsyncComponent(() => import('@/features/demo/DemoTool.vue')) : null
const SHOW_DEMO = !!DemoTool

const route = useRoute()

const plain = computed(() => !!route.meta.plain)

// 주소의 사업장 ID (/sites/:siteId/...)
const siteId = computed(() => route.params.siteId)

// 상단 바에 보여 줄 사업장 이름: 사업장 ID 가 바뀔 때마다 조회 (실패하면 비워 둠)
const siteName = ref('')
watch(
  siteId,
  async (id) => {
    if (!id) return
    siteName.value = ''
    try {
      siteName.value = await getSiteName(id)
    } catch {
      // 조회 실패 시 이름 없이 표시
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="main-layout">
    <AppHeader :title="plain ? route.meta.title : ''">
      <template v-if="!plain">
        <SitePill :name="siteName" />
        <AppNav />
      </template>
    </AppHeader>

    <main class="content">
      <PageTransition />
    </main>

    <!-- 주의·체크리스트 창, 알림 띠, 화면 테두리 (body 로 옮겨 그림) -->
    <AlertHost />

    <!-- 채팅 패널: 상단 바 채팅 아이콘으로 열고 닫음 (body 로 옮겨 그림, 화면을 바꿔도 열린 채로) -->
    <ChatPanel />

    <!-- 상황 바꾸기 도구: 개발 모드 + 예시 데이터일 때만 (배포 빌드에는 없음) -->
    <DemoTool v-if="SHOW_DEMO" />
  </div>
</template>

<style scoped>
.content {
  padding: 16px 20px 24px;
  /* 확대·축소 중 커진 화면이 넘쳐도 스크롤 길이·스크롤바가 바뀌지 않게 (가로·세로 모두 잘라냄) */
  overflow: clip;
}
</style>
