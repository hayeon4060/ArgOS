<!--
  센서 추이 한 줄 (설계안 views.css .v-equip .tr: 왼쪽 이름·지금 값 + 오른쪽 그래프)
  - 세 줄의 그래프는 group 으로 묶여 확대·이동·마우스 세로선이 같이 움직임 (TrendCard 에서 connect)
-->
<script setup>
import { computed } from 'vue'
import { trendOption } from './trendOption'
import AppIcon from '@/components/AppIcon.vue'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  /** 센서 종류 정보 (SENSOR_TYPES 한 줄) */
  type: { type: Object, required: true },
  /** 설비 상세의 센서 */
  sensor: { type: Object, required: true },
  /** GET /sensors/series 의 이 센서 줄 */
  series: { type: Object, required: true },
  liveValue: { type: Number, default: null },
  showPrev: { type: Boolean, default: true },
  wholeDay: { type: Boolean, default: false },
  zoom: { type: Object, required: true },
  /** 그래프 묶음 이름 */
  group: { type: String, required: true },
})

const emit = defineEmits(['zoom'])

const option = computed(() =>
  trendOption({
    series: props.series,
    level: props.sensor.level,
    dec: props.type.dec,
    liveValue: props.liveValue,
    showPrev: props.showPrev,
    wholeDay: props.wholeDay,
    zoom: props.zoom,
  }),
)

// 값이 아직 없으면 '–' (예전엔 null → '0.0' 으로 보였음)
const value = computed(() => {
  const v = props.liveValue ?? props.sensor.value
  return Number.isFinite(v) ? v.toFixed(props.type.dec) : '–'
})
// sensor.level 은 화면에 보여 줄 단계 (EquipmentPage 에서 지금 값과 기준을 비교해 다시 정함 → sensorLevel.js)
const valueClass = computed(() => ({
  warn: props.sensor.level === LEVEL.WARN,
  alarm: props.sensor.level === LEVEL.ALARM || props.sensor.level === LEVEL.FIRE,
}))

/** 휠·끌기로 확대 구간이 바뀌면 카드에 알려 줌 (−/+ 단추가 이어서 계산하도록) */
function onZoom(e) {
  const z = e.batch?.[0] ?? e
  if (z.start != null && z.end != null) emit('zoom', { start: z.start, end: z.end })
}
</script>

<template>
  <div class="row">
    <div class="name">
      <b><AppIcon :name="type.icon" :size="16" />{{ type.label }}</b>
      <div class="tag mono">{{ sensor.tag }}</div>
      <div class="value mono" :class="valueClass">{{ value }}<small>{{ sensor.unit }}</small></div>
    </div>
    <VChart class="chart" :option="option" :group="group" autoresize @datazoom="onZoom" />
  </div>
</template>

<style scoped>
.row {
  display: flex;
  height: 146px;
  border-bottom: 1px solid #f1f5f9;
}

.row:last-child {
  border-bottom: none;
}

.name {
  width: 170px;
  flex: none;
  padding: 16px 0 0 20px;
}

.name b {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
}

.name b .app-icon {
  color: var(--color-text-sub);
}

.tag {
  margin-top: 3px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.value {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 600;
  color: var(--color-text);
}

.value.warn {
  color: var(--color-level-warn); /* #F97316 */
}

.value.alarm {
  color: var(--color-level-alarm); /* #DC2626 */
}

.value small {
  margin-left: 3px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.chart {
  flex: 1;
  min-width: 0;
  height: 140px;
  margin: 4px 10px 0 0;
}
</style>
