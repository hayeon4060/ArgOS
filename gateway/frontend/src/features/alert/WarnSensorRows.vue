<!--
  주의 상세 '센서 데이터' 상자: 알림이 났을 때 센서 3종(전류 · 온도 · CO) 값 · 기준 · 단계
  - 2026-11-19 재원님 요청으로 '센서 데이터 · 열화상 확인' 창을 주의 상세에 합치면서 그 창의 센서 줄을 옮겨 옴
  - sensors: GET /alarms/{id}/detail 의 sensors [{ tag, type, value, unit, warn, alarm, level }]
-->
<script setup>
import SensorRing from '@/features/process/SensorRing.vue'
import { LEVEL_META, sensorTypeOf } from '@/types/codes'

defineProps({
  sensors: { type: Array, required: true },
})

const fmt = (s) => Number(s.value).toFixed(sensorTypeOf(s.type)?.dec ?? 0)
const keyOf = (s) => LEVEL_META[s.level]?.key
</script>

<template>
  <div class="m-box">
    <div v-for="s in sensors" :key="s.tag" class="vrow">
      <SensorRing :sensor="s" :size="42" />
      <div class="n">{{ sensorTypeOf(s.type)?.label }}<b class="mono">{{ s.tag }}</b></div>
      <div class="v mono" :class="`is-${keyOf(s)}`">{{ fmt(s) }}<small>{{ s.unit }}</small></div>
      <div class="th">주의 {{ s.warn }} · 경보 {{ s.alarm }}</div>
      <span class="m-chip" :class="keyOf(s) === 'normal' ? 'ok' : keyOf(s)">{{ LEVEL_META[s.level]?.label }}</span>
    </div>
  </div>
</template>

<style scoped>
/* 센서 한 줄 (설계안 .vrow) */
.vrow {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
}

.vrow:last-child {
  border-bottom: none;
}

.n {
  width: 60px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.n b {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.v {
  width: 100px;
  font-size: 21px;
  font-weight: 600;
}

.v small {
  margin-left: 3px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.v.is-warn {
  color: var(--color-level-warn-text);
}

.v.is-alarm {
  color: var(--color-level-alarm-text);
}

.th {
  flex: 1;
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}
</style>
