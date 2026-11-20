<!--
  진행 고리 (완료 수 / 전체 수) — 설계안 lib.js ring()
  - 숫자가 바뀌면 고리가 400ms 에 걸쳐 참 (화면설계서 C3 · I1, --m-value)
  - 가운데 내용은 슬롯으로 (예: '2/5 완료')
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, required: true },
  total: { type: Number, required: true },
  size: { type: Number, default: 28 },
  stroke: { type: Number, default: 4 },
  color: { type: String, default: 'var(--color-primary)' },
  track: { type: String, default: '#e2e8f0' },
})

const r = computed(() => (props.size - props.stroke) / 2)
const length = computed(() => 2 * Math.PI * r.value)
const dash = computed(() => `${(length.value * Math.min(props.value, props.total)) / (props.total || 1)} ${length.value}`)
</script>

<template>
  <span class="progress-ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" aria-hidden="true">
      <circle :cx="size / 2" :cy="size / 2" :r="r" fill="none" :stroke="track" :stroke-width="stroke" />
      <circle
        class="bar"
        :cx="size / 2"
        :cy="size / 2"
        :r="r"
        fill="none"
        :stroke="color"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="dash"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
      />
    </svg>
    <span v-if="$slots.default" class="center"><slot /></span>
  </span>
</template>

<style scoped>
.progress-ring {
  position: relative;
  display: inline-block;
  flex: none;
}

.bar {
  transition:
    stroke-dasharray var(--m-value),
    stroke 300ms;
}

.center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
</style>
