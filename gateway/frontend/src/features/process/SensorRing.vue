<!--
  작은 원형 게이지 (설계안 lib.js ring()) — 센서 값이 눈금 끝 대비 얼마나 찼는지
  - 눈금 끝: 경보 기준의 1.2배 (설계안과 같음)
  - 색: 센서 단계 색
-->
<script setup>
import { computed } from 'vue'
import { LEVEL_META } from '@/types/codes'

const props = defineProps({
  /** { value, alarm, level } */
  sensor: { type: Object, required: true },
  size: { type: Number, default: 50 },
  stroke: { type: Number, default: 6 },
})

const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const ratio = computed(() => Math.min(1, Math.max(0, props.sensor.value / (props.sensor.alarm * 1.2))))
const color = computed(() => LEVEL_META[props.sensor.level]?.color ?? 'var(--color-level-normal)')
</script>

<template>
  <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" aria-hidden="true">
    <circle :cx="size / 2" :cy="size / 2" :r="radius" fill="none" stroke="#EEF2F6" :stroke-width="stroke" />
    <circle
      :cx="size / 2"
      :cy="size / 2"
      :r="radius"
      fill="none"
      :stroke="color"
      :stroke-width="stroke"
      stroke-linecap="round"
      :stroke-dasharray="`${(circumference * ratio).toFixed(1)} ${circumference.toFixed(1)}`"
      :transform="`rotate(-90 ${size / 2} ${size / 2})`"
      class="arc"
    />
  </svg>
</template>

<style scoped>
/* 값이 바뀌면 호 길이·색이 이어서 변함 (화면설계서 R2 --m-value) */
.arc {
  transition:
    stroke-dasharray var(--m-value),
    stroke var(--m-value);
}
</style>
