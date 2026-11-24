<!--
  시간대별 알림 차트 (ECharts): 주의·경보 막대(쌓기) + 조치 선
  - hours: 알림이 있는 시간만 온 값 → 24칸(00~23시)을 모두 채워서 그림
  - 2026-11-20: 주간·야간 12칸 두 줄 → 하루 24칸 한 줄 (startHour 없앰)
-->
<script setup>
import { computed } from 'vue'
import { CHART_COLORS } from '@/utils/chartColors'

const props = defineProps({
  /** [{ hour, warn, alarm, action }] */
  hours: { type: Array, required: true },
})

const pad = (n) => String(n).padStart(2, '0')

// 24칸 채우기: 값이 없는 시간은 0
const slots = computed(() =>
  Array.from({ length: 24 }, (_, hour) => {
    const found = props.hours.find((h) => h.hour === hour)
    return { hour, warn: found?.warn ?? 0, alarm: found?.alarm ?? 0, action: found?.action ?? 0 }
  }),
)

// ECharts 설정 (설계안 v-rev.js hourBar() 와 같은 모양, 칸이 24개라 막대·글자 간격만 조정)
const option = computed(() => ({
  // 처음 그리기 500ms, 값 바뀜 400ms (화면설계서 R2 --m-chart-in, --m-value)
  animationDuration: 500,
  animationDurationUpdate: 400,
  grid: { left: 26, right: 10, top: 14, bottom: 24 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: {
    type: 'category',
    data: slots.value.map((s) => pad(s.hour)),
    axisLine: { lineStyle: { color: CHART_COLORS.grid } },
    axisTick: { show: false },
    // 24칸 글자가 붙지 않게 2시간마다 (00, 02, … 22)
    axisLabel: { color: CHART_COLORS.axis, fontSize: 10.5, interval: 1 },
  },
  yAxis: {
    type: 'value',
    // 기본 높이 4건, 4건이 넘는 시간이 있으면 그만큼 늘림 (전에는 4 고정이라 막대가 잘렸음)
    max: (v) => Math.max(4, v.max),
    minInterval: 1,
    splitLine: { lineStyle: { color: CHART_COLORS.grid } },
    axisLabel: { color: CHART_COLORS.axis, fontSize: 10.5 },
  },
  series: [
    { name: '주의', type: 'bar', stack: 'alarm', barWidth: 10, data: slots.value.map((s) => s.warn), itemStyle: { color: CHART_COLORS.warn } },
    { name: '경보', type: 'bar', stack: 'alarm', barWidth: 10, data: slots.value.map((s) => s.alarm), itemStyle: { color: CHART_COLORS.alarm } },
    {
      name: '조치',
      type: 'line',
      data: slots.value.map((s) => s.action),
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: CHART_COLORS.action, width: 2 },
      itemStyle: { color: CHART_COLORS.action },
    },
  ],
}))
</script>

<template>
  <!-- VChart 는 main.js 에서 전역 등록 (utils/echarts.js). 높이를 정해 줘야 그려짐 -->
  <VChart class="hourly-chart" :option="option" autoresize />
</template>

<style scoped>
.hourly-chart {
  width: 100%;
  height: 210px; /* 두 줄(104px × 2)을 한 줄로 합친 높이 — 카드 높이를 전과 같게 유지 */
}
</style>
