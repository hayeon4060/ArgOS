<!--
  화면 뼈대 (데이터·화면 파일을 받는 동안 '불러오는 중' 글자 대신 보이는 회색 칸 배치)
  - 모양 2종: shape="dashboard" (왼쪽 큰 칸 + 오른쪽 카드) / "table" (조건 줄 + 숫자 칸·표·차트)
  - 칸 배치는 화면마다 실제 카드와 같게 skeletonLayouts.js 에 있음 → layout 에 화면(라우트) 이름을 넘김
  - 0.2초 지연: 0.2초 안에 사라지면 아예 보이지 않음 (CSS animation-delay, 깜빡임 방지)
    · instant: 지연 없이 바로 (라우터가 이미 뼈대를 보여 주고 있었을 때 — 같은 뼈대가 이어지게)
  - body: 화면 위쪽 조건·탭 줄(bar)은 빼고 아래 본문 칸만 (조건 줄은 그대로 보이는 화면용, 예: 공장 관리 '조회')
  - 높이: 본문 영역(화면 − 상단 바 − 본문 위아래 여백 40px)을 채움. 화면 배율에서도 맞게 100vh 대신 --screen-h
  - 접근성: role="status" + aria-busy, 화면 낭독기에는 '불러오는 중'만 읽힘
  - 사용: <PageSkeleton v-if="loading" shape="dashboard" layout="process" />
-->
<script setup>
import { computed } from 'vue'
import { consumeSkeletonCarry } from '@/features/motion/routeSkeleton'
import SkeletonBlock from './SkeletonBlock.vue'
import { layoutOf } from './skeletonLayouts'

const props = defineProps({
  /** 뼈대 모양: 'dashboard' | 'table' */
  shape: { type: String, default: 'dashboard' },
  /** 화면(라우트) 이름 — skeletonLayouts.js 에서 이 화면의 칸 배치를 찾음 */
  layout: { type: String, default: '' },
  /** 위쪽 조건·탭 줄을 빼고 본문 칸만 */
  body: { type: Boolean, default: false },
  /** 0.2초 기다리지 않고 바로 보임 */
  instant: { type: Boolean, default: false },
})

/**
 * 바로 보일지: instant 이거나, 방금 라우터 뼈대가 보이고 있었으면 (그 뼈대를 이어받음)
 * - 처음 그릴 때 한 번만 정함 (이어받기 표시는 한 번 쓰면 지워짐)
 */
const now = props.instant || consumeSkeletonCarry()

/** 이 화면의 칸 배치 (skeletonLayouts.js) */
const layout = computed(() => layoutOf(props.shape, props.layout))

/** 그릴 줄: body 면 조건·탭 줄 제외 */
const rows = computed(() => (props.body ? layout.value.rows.filter((r) => !r.bar) : layout.value.rows))

/** 칸 이름 목록 (같은 이름은 하나의 칸으로 합쳐지므로 한 번씩만) */
const areas = computed(() => [...new Set(rows.value.flatMap((r) => r.areas.split(' ')))])

/**
 * 격자 모양 + 높이
 * - body 면 빠진 줄 높이 + 줄 사이 간격(16px)만큼 높이를 줄임 → 위에 남은 실제 조건 줄과 합쳐 본문을 채움
 * - 배치에 trim 이 있으면 그만큼 더 줄임 (실제 화면이 본문보다 조금 짧은 경우)
 */
const gridStyle = computed(() => {
  const bars = props.body ? layout.value.rows.filter((r) => r.bar) : []
  const removed = bars.map((r) => ` - ${r.size} - 16px`).join('') + (layout.value.trim ? ` - ${layout.value.trim}` : '')
  return {
    gridTemplateColumns: layout.value.columns,
    gridTemplateRows: rows.value.map((r) => r.size).join(' '),
    gridTemplateAreas: rows.value.map((r) => `"${r.areas}"`).join(' '),
    height: `calc(var(--screen-h) - var(--topbar-height) - 40px${removed})`,
  }
})
</script>

<template>
  <div class="page-skeleton" :class="{ now }" :style="gridStyle" role="status" aria-busy="true" aria-live="polite">
    <span class="sr-only">불러오는 중</span>

    <template v-for="name in areas" :key="name">
      <!-- 탭 · 경로 줄: 왼쪽(탭 · 경로)과 오른쪽(단추) 작은 칸 -->
      <div v-if="name === 'tab'" class="tab" :style="{ gridArea: name }">
        <SkeletonBlock class="tab-left" />
        <SkeletonBlock class="tab-right" />
      </div>

      <!-- 숫자 칸 4개 -->
      <div v-else-if="name === 'kpi'" class="kpi" :style="{ gridArea: name }">
        <SkeletonBlock v-for="n in 4" :key="n" />
      </div>

      <!-- 카드 한 장 자리 -->
      <SkeletonBlock v-else :style="{ gridArea: name }" />
    </template>
  </div>
</template>

<style scoped>
.page-skeleton {
  position: relative; /* 낭독기용 글자(.sr-only)의 기준 */
  display: grid;
  gap: 16px;
  /* 0.2초 기다렸다가 살짝 나타남 (그 전에 사라지면 보이지 않음) */
  animation: sk-show var(--m-pop-in) var(--m-skeleton-delay) both;
}

/* 이어받는 뼈대: 기다림·나타남 없이 바로 */
.page-skeleton.now {
  animation: none;
}

@keyframes sk-show {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 탭 · 경로 줄: 왼쪽 탭 자리 + 오른쪽 단추 자리 (실제 탭·단추 크기 정도) */
.tab {
  display: flex;
  justify-content: space-between;
  min-width: 0;
}

.tab-left {
  width: 240px;
}

.tab-right {
  width: 200px;
}

/* 숫자 칸 4개 (통계 화면 요약 카드와 같은 4칸) */
.kpi {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  min-width: 0;
}

/* 화면 낭독기에만 읽히는 글자 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
