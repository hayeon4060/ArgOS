<!--
  센서 단계 도넛 (정상·주의·경보 비율 + 가운데 합계)
  - 화면설계안 js/lib.js donut() 과 같은 모양을 SVG 로 직접 그림 (ECharts 쓰기엔 과해서)
  - 사용 예: <SensorDonut :counts="{ NORMAL: 112, WARN: 0, ALARM: 0 }" label="센서" />
-->
<script setup>
import { computed } from 'vue'
import { totalOf } from './siteStats'
import { LEVEL_META, SENSOR_STAGES } from '@/types/codes'

const props = defineProps({
  /** { NORMAL, WARN, ALARM } */
  counts: { type: Object, required: true },
  /** 가운데 합계 아래 작은 글자 */
  label: { type: String, default: '' },
  size: { type: Number, default: 128 },
  stroke: { type: Number, default: 14 },
})

const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const total = computed(() => totalOf(props.counts))

// 단계별 호(arc): 원 둘레를 개수 비율로 나누고, 조각 사이에 2px 틈을 둠
const arcs = computed(() => {
  let offset = 0
  return SENSOR_STAGES.map((level) => {
    const value = props.counts[level]
    if (!value) return null
    const len = (circumference.value * value) / total.value
    const arc = {
      level,
      color: LEVEL_META[level].color,
      dash: `${Math.max(0, len - 2).toFixed(1)} ${(circumference.value - len + 2).toFixed(1)}`,
      offset: (-offset).toFixed(1),
    }
    offset += len
    return arc
  }).filter(Boolean)
})
</script>

<template>
  <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="`센서 ${total}개`">
    <!-- 바탕 원 -->
    <circle :cx="size / 2" :cy="size / 2" :r="radius" fill="none" stroke="#EEF2F6" :stroke-width="stroke" />
    <!-- 단계별 조각 (12시 방향부터 시계 방향) -->
    <circle
      v-for="arc in arcs"
      :key="arc.level"
      :cx="size / 2"
      :cy="size / 2"
      :r="radius"
      fill="none"
      :stroke="arc.color"
      :stroke-width="stroke"
      :stroke-dasharray="arc.dash"
      :stroke-dashoffset="arc.offset"
      :transform="`rotate(-90 ${size / 2} ${size / 2})`"
    />
    <text
      x="50%"
      :y="label ? '45%' : '50%'"
      text-anchor="middle"
      dominant-baseline="central"
      class="total"
      :font-size="size * 0.26"
    >
      {{ total }}
    </text>
    <text v-if="label" x="50%" y="68%" text-anchor="middle" class="label">{{ label }}</text>
  </svg>
</template>

<style scoped>
.total {
  font-family: var(--font-mono);
  font-weight: 600;
  fill: var(--color-text);
}

.label {
  font-size: 11px;
  fill: var(--color-text-weak);
}

/* 개수가 바뀌면 조각 길이가 이어서 변함 (화면설계서 R2 --m-value) */
circle {
  transition:
    stroke-dasharray var(--m-value),
    stroke-dashoffset var(--m-value);
}
</style>
