<!--
  상태별 건수 칩 — '완료 2' '예정 1' '누락 1' (수정안 2. 숫자만 두지 않고 글자 + 숫자)
  - 0건인 상태는 그리지 않음
  - 누락은 진한 빨강 (달력에서 눈에 띄게)
-->
<script setup>
import InspBadge from './InspBadge.vue'
import { SCHEDULE_STATE, STATE_ORDER } from './inspectionMeta'

defineProps({
  /** { DONE, PLANNED, DELAYED } */
  counts: { type: Object, required: true },
})
</script>

<template>
  <span class="counts">
    <template v-for="s in STATE_ORDER" :key="s">
      <InspBadge v-if="counts[s]" :tone="SCHEDULE_STATE[s].cls" :solid="s === 'DELAYED'">
        {{ SCHEDULE_STATE[s].label }} <b class="mono">{{ counts[s] }}</b>
      </InspBadge>
    </template>
  </span>
</template>

<style scoped>
.counts {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.counts :deep(.kd) {
  height: 20px;
  padding: 0 6px;
  font-size: 11.5px;
  font-weight: 600;
}

b {
  font-weight: 600;
}
</style>
