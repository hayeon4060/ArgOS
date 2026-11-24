<!--
  점검 관리 작은 배지 — 상태(완료 · 예정 · 누락) · 구분(수기 · 전자) · 결과(양호 · 불량)
  - 사용 예: <InspBadge :tone="SCHEDULE_STATE[it.status].cls">{{ SCHEDULE_STATE[it.status].label }}</InspBadge>
  - tone: done 초록 / plan 파랑 / miss 빨강 / paper 회색 / digital 청록
  - solid: 진한 바탕 (달력 칸의 누락처럼 눈에 띄어야 할 때)
-->
<script setup>
defineProps({
  tone: { type: String, required: true },
  solid: { type: Boolean, default: false },
})
</script>

<template>
  <span class="kd" :class="[tone, { solid }]"><slot /></span>
</template>

<style scoped>
.kd {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 7px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.done {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

.plan {
  background: #dbeafe;
  color: var(--color-primary-hover);
}

.miss {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.miss.solid {
  background: var(--color-level-alarm);
  color: #fff;
}

.paper {
  background: #f1f5f9;
  color: #334155;
}

.digital {
  background: #ecfeff;
  color: #0e7490;
}
</style>
