<!--
  본문 화면 전환 (레이아웃의 RouterView 를 감싸서 씀)
  - 효과 이름은 pageTransition.js 가 라우터에서 정한 값 (zoom-in / zoom-out / page)
  - 효과 모양(시간·크기)은 assets/styles/motion.css, 시간 값은 tokens.css
  - 카드가 차례로 올라오며 나타나는 효과는 BaseCard 가 스스로 함 (데이터를 늦게 받아도 적용되게)
  - 화면 파일을 받느라 이동이 0.2초 넘게 걸리면 가려는 화면의 뼈대를 대신 보여 줌 (routeSkeleton.js)
    · 이전 화면 → 뼈대는 원래 효과 그대로, 뼈대 → 새 화면은 효과 없이 바로 (:css="false")
    · 상단 바 · 알림 · 채팅 창은 이 칸 밖이라 가려지지 않음
  - 사용: <PageTransition /> (안에서 RouterView 를 그림) → 새 화면도 자동으로 적용
-->
<script setup>
import { ref, watch } from 'vue'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import { transitionState } from './pageTransition'
import { SKELETON_SWAP, routeSkeleton } from './routeSkeleton'
import { toLayoutPx } from '@/utils/screenScale'

const box = ref(null)
const originStyle = ref({})

/**
 * 확대·축소 중심: 누른 위치(화면 기준 px)를 이 칸 기준 위치로 바꿔서 CSS 변수로 넘김
 * (transform-origin 은 움직이는 요소 기준이라서)
 */
watch(
  () => [transitionState.originX, transitionState.originY, transitionState.name],
  () => {
    const rect = box.value?.getBoundingClientRect() ?? { left: 0, top: 0 }
    originStyle.value = {
      // 누른 위치·rect 는 확대된 화면 기준 → CSS px 로 바꿈 (utils/screenScale.js)
      '--zoom-origin': `${toLayoutPx(transitionState.originX - rect.left)}px ${toLayoutPx(transitionState.originY - rect.top)}px`,
    }
  },
)
</script>

<template>
  <div ref="box" class="page-transition" :style="originStyle">
    <RouterView v-slot="{ Component, route }">
      <Transition :name="transitionState.name" :css="transitionState.name !== SKELETON_SWAP" mode="out-in" appear>
        <!-- 화면 파일을 받는 중: 가려는 화면의 뼈대 (이미 0.2초를 기다렸으므로 바로 보임) -->
        <div v-if="routeSkeleton.visible" key="route-skeleton" class="page">
          <PageSkeleton :shape="routeSkeleton.shape" :layout="routeSkeleton.layout" instant />
        </div>
        <!-- 같은 화면 안에서 주소만 바뀌어도(예: 다른 공장) 다시 그리도록 전체 주소를 key 로 -->
        <div v-else :key="route.fullPath" class="page">
          <component :is="Component" />
        </div>
      </Transition>
    </RouterView>
  </div>
</template>
