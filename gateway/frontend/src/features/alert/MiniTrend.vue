<!--
  알림 창 안의 작은 추이 그래프 (설계안 trendA(): 'CT-17 · 최근 40분', 주의·경보 기준선)
  - 설비 상세 추이와 같은 그림 설정(features/equipment/trendOption.js)을 씀 → 선 500ms 로 그려짐
-->
<script setup>
import { computed } from 'vue'
import { trendOption } from '@/features/equipment/trendOption'

const props = defineProps({
  /** { tag, unit, warn, alarm, points: [[시각, 값]] } (백엔드 AlarmService.detail 의 trend) */
  trend: { type: Object, required: true },
  title: { type: String, required: true },
  sub: { type: String, default: '' },
  level: { type: String, default: 'WARN' },
  height: { type: Number, default: 176 },
  /** 소수 자리 (전류 1, 온도·CO 0) */
  dec: { type: Number, default: 1 },
})

const option = computed(() =>
  trendOption({
    series: { ...props.trend, compare: [] },
    level: props.level,
    dec: props.dec,
    liveValue: null,
    showPrev: false,
    wholeDay: false,
    zoom: { start: 0, end: 100 },
  }),
)
</script>

<template>
  <div class="m-box mini">
    <div class="head">
      {{ title }} <span class="mono">{{ sub }}</span>
    </div>
    <VChart class="chart" :style="{ height: `${height}px` }" :option="option" autoresize />
  </div>
</template>

<style scoped>
.mini {
  position: relative;
  padding-top: 30px;
}

.head {
  position: absolute;
  left: 18px;
  top: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.head .mono {
  font-weight: 500;
  color: var(--color-text-weak);
}

.chart {
  width: 100%;
}
</style>
