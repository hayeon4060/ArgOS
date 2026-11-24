<!--
  센서 게이지 하나 (설계안 vEquip() '센서 현재값'의 .g)
  - 고리 게이지 + '전류 CT-17' + 기준 글자 두 줄
    · 정상: '평소 18.0 A'  / 주의·경보: '주의 기준 21.6 A 초과' (주황 #F97316 · 빨강 #DC2626 굵게)
      (서버가 평소 값(usual)을 아직 안 줌 → 값이 없으면 '정상 범위', 예전엔 '평소 NaN' 이 보였음)
    · '주의 21.6 · 경보 27 A' (주의 값 주황 · 경보 값 빨강, 2026-11-22 재원님 요청)
  - sensor 가 없으면(이 설비에 그 종류 센서가 없음) 점선 동그라미 + '전류 센서 없음'
  - sensor.level 은 화면에 보여 줄 단계 (EquipmentPage 에서 지금 값과 기준을 비교해 다시 정함 → utils/sensorLevel.js)
-->
<script setup>
import { computed } from 'vue'
import { gaugeOption } from './gaugeOption'
import AppIcon from '@/components/AppIcon.vue'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  /** 센서 종류 정보 (types/codes.js SENSOR_TYPES 한 줄) */
  type: { type: Object, required: true },
  /** 설비 상세의 센서 (없으면 null) */
  sensor: { type: Object, default: null },
  /** 지금 값 (실시간으로 바뀌는 값, 없으면 sensor.value) */
  value: { type: Number, default: null },
})

const now = computed(() => props.value ?? props.sensor?.value)

const option = computed(() =>
  gaugeOption({
    value: now.value,
    max: props.sensor.max,
    warn: props.sensor.warn,
    alarm: props.sensor.alarm,
    unit: props.sensor.unit,
    level: props.sensor.level,
    dec: props.type.dec,
  }),
)

const fmt = (v) => Number(v).toFixed(props.type.dec)

const isWarn = computed(() => props.sensor?.level === LEVEL.WARN)
const isAlarm = computed(() => props.sensor?.level === LEVEL.ALARM || props.sensor?.level === LEVEL.FIRE)

/** 첫째 줄: 평소 값 또는 넘은 기준 */
const stateText = computed(() => {
  const s = props.sensor
  if (isWarn.value) return `주의 기준 ${s.warn} ${s.unit} 초과`
  if (isAlarm.value) return `경보 기준 ${s.alarm} ${s.unit} 초과`
  // 평소 값이 없으면 (지금 서버 응답) NaN 대신 '정상 범위'
  return Number.isFinite(s.usual) ? `평소 ${fmt(s.usual)} ${s.unit}` : '정상 범위'
})
</script>

<template>
  <div class="gauge">
    <template v-if="sensor">
      <VChart class="chart" :option="option" autoresize />
      <div class="name">
        <AppIcon :name="type.icon" :size="15" />{{ type.label }} <span class="mono">{{ sensor.tag }}</span>
      </div>
      <div class="th" :class="{ warn: isWarn, alarm: isAlarm }">
        {{ stateText }}
      </div>
      <div class="th">
        <span class="lv-warn">주의 {{ sensor.warn }}</span> · <span class="lv-alarm">경보 {{ sensor.alarm }}</span> {{ sensor.unit }}
      </div>
    </template>

    <template v-else>
      <div class="none">
        <span class="ring"><AppIcon :name="type.icon" :size="24" /></span>
      </div>
      <div class="name off">{{ type.label }} 센서 없음</div>
      <div class="th">이 설비에는 연결되지 않았습니다</div>
    </template>
  </div>
</template>

<style scoped>
.gauge {
  text-align: center;
  min-width: 0;
}

.chart {
  height: 250px;
}

.name {
  margin-top: -10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.name .app-icon {
  color: var(--color-text-sub);
}

.name .mono {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.name.off {
  margin-top: 0;
  color: var(--color-text-weak);
}

.th {
  margin-top: 5px;
  font-size: 12px;
  color: var(--color-text-weak);
  white-space: nowrap;
}

.th.warn {
  color: var(--color-level-warn); /* #F97316 */
  font-weight: 700;
}

.th.alarm {
  color: var(--color-level-alarm); /* #DC2626 */
  font-weight: 700;
}

/* 아래 줄 기준 값: 단계 색 */
.lv-warn {
  color: var(--color-level-warn); /* #F97316 */
  font-weight: 600;
}

.lv-alarm {
  color: var(--color-level-alarm); /* #DC2626 */
  font-weight: 600;
}

/* 센서 없음: 점선 동그라미 */
.none {
  height: 250px;
  display: grid;
  place-items: center;
}

.ring {
  width: 150px;
  height: 150px;
  border: 2px dashed var(--color-border-strong);
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--color-border-strong);
}
</style>
