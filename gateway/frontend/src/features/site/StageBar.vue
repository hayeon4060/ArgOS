<!--
  센서 단계 막대 (경보·주의·정상 순서로 비율만큼 칠함)
  - 화면설계안 js/v-rev.js stageBar() 와 같은 모양
  - 사용 예: <StageBar :counts="{ NORMAL: 19, WARN: 1, ALARM: 0 }" />
-->
<script setup>
import { computed } from 'vue'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  /** { NORMAL, WARN, ALARM } */
  counts: { type: Object, required: true },
})

// 경보·주의는 개수가 적어도 보이도록 최소 너비를 둠, 정상은 연한 초록
const parts = computed(() =>
  [
    { level: LEVEL.ALARM, color: 'var(--color-level-alarm)', min: 10 },
    { level: LEVEL.WARN, color: 'var(--color-level-warn)', min: 10 },
    { level: LEVEL.NORMAL, color: '#86EFAC', min: 0 },
  ]
    .map((p) => ({ ...p, value: props.counts[p.level] ?? 0 }))
    .filter((p) => p.value > 0),
)
</script>

<template>
  <span class="stage-bar">
    <i
      v-for="p in parts"
      :key="p.level"
      :style="{ flex: p.value, background: p.color, minWidth: `${p.min}px` }"
    />
  </span>
</template>

<style scoped>
.stage-bar {
  flex: 1;
  min-width: 60px;
  height: 8px;
  display: flex;
  gap: 2px;
  border-radius: 3px;
  overflow: hidden;
}

.stage-bar i {
  display: block;
  height: 100%;
  border-radius: 2px;
  transition: flex-grow var(--m-value); /* 개수가 바뀌면 길이가 이어서 변함 */
}
</style>
