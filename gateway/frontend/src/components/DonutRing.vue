<!--
  여러 조각 도넛 (가운데 내용은 슬롯)
  - 사용 예: <DonutRing :parts="[{ value: 2, color: '#0f172a' }, { value: 3, color: '#2563eb' }]" :size="132" :stroke="16">29<small>명</small></DonutRing>
  - 값이 바뀌면 조각 길이가 0.4초에 바뀜 (--m-value)
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** [{ value, color }] */
  parts: { type: Array, required: true },
  size: { type: Number, default: 132 },
  stroke: { type: Number, default: 16 },
  track: { type: String, default: '#eef2f6' },
})

const r = computed(() => (props.size - props.stroke) / 2)
const c = computed(() => 2 * Math.PI * r.value)
const segs = computed(() => {
  const total = props.parts.reduce((s, p) => s + p.value, 0) || 1
  let start = 0
  return props.parts.map((p, i) => {
    const len = (p.value / total) * c.value
    // 조각 사이 2px 틈
    const seg = { i, color: p.color, dash: `${Math.max(0, len - (p.value ? 2 : 0))} ${c.value}`, offset: -start }
    start += len
    return seg
  })
})
</script>

<template>
  <span class="donut" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" aria-hidden="true">
      <circle :cx="size / 2" :cy="size / 2" :r="r" fill="none" :stroke="track" :stroke-width="stroke" />
      <circle
        v-for="s in segs"
        :key="s.i"
        :cx="size / 2"
        :cy="size / 2"
        :r="r"
        fill="none"
        :stroke="s.color"
        :stroke-width="stroke"
        :stroke-dasharray="s.dash"
        :stroke-dashoffset="s.offset"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
        class="seg"
      />
    </svg>
    <span class="mid"><slot /></span>
  </span>
</template>

<style scoped>
.donut {
  position: relative;
  display: inline-block;
  flex: none;
}

.seg {
  transition:
    stroke-dasharray var(--m-value),
    stroke-dashoffset var(--m-value);
}

.mid {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: calc(50% - 18px);
}
</style>
