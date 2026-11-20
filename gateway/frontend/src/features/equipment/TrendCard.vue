<!--
  '센서 추이' 카드 (설계안 vEquip() #ct)
  - 센서마다 한 줄씩 (전류 · 온도 · CO 순서), 세 그래프는 한 묶음이라 확대·이동·마우스 세로선이 같이 움직임
  - 제목줄: 범위(1시간 / 6시간 / 오늘), '전날 같이 보기' 스위치, 확대 단추(− / 전체 / +), 범례(전날 · 주의 기준 · 경보 기준)
    (범위 버튼은 2026-11-19 설비 상세 제목 줄에서 이 카드로 옮김)
  - 범위를 바꾸면(v-model:pick) 페이지가 다시 불러오고, 그동안 카드가 흐려짐(loading)
-->
<script setup>
import { connect } from 'echarts/core'
import { computed, ref, watch } from 'vue'
import TrendRow from './TrendRow.vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import SegButtons from '@/components/SegButtons.vue'
import { SENSOR_TYPES } from '@/types/codes'

const props = defineProps({
  /** 설비 상세의 센서 목록 */
  sensors: { type: Array, required: true },
  /** GET /sensors/series 응답 (불러오기 전이면 null) */
  data: { type: Object, default: null },
  /** 지금 그려진 값의 범위 (새 값이 올 때까지 이전 범위) */
  range: { type: String, required: true },
  loading: { type: Boolean, default: false },
  liveValues: { type: Object, default: () => ({}) },
})

/** 고른 범위 ('1h' | '6h' | 'today') — 버튼은 바로 바뀌고, 그림은 새 값이 오면 바뀜(range) */
const pick = defineModel('pick', { type: String, required: true })

const RANGES = [
  { value: '1h', label: '1시간' },
  { value: '6h', label: '6시간' },
  { value: 'today', label: '오늘' },
]

const RANGE_LABEL = { '1h': '최근 1시간', '6h': '최근 6시간', today: '오늘 00시부터' }

// 세 그래프 묶기: 같은 group 이름을 가진 그래프끼리 확대·툴팁이 같이 움직임
const GROUP = 'equipment-trend'
connect(GROUP)

const showPrev = ref(true)

/** 확대 구간 (%) — 설계안 확대 단추와 같은 계산 */
const zoom = ref({ start: 0, end: 100 })
function zoomBy(step) {
  const { start, end } = zoom.value
  const w = end - start
  if (step === 0) zoom.value = { start: 0, end: 100 }
  else if (step > 0) zoom.value = { start: Math.min(start + w * 0.3, 90), end: 100 }
  else zoom.value = { start: Math.max(0, start - w * 0.6), end: 100 }
}
// 범위를 바꾸면 전체 보기로
watch(
  () => props.range,
  () => (zoom.value = { start: 0, end: 100 }),
)

/** 줄 목록: 센서 종류 순서대로, 추이 값이 있는 센서만 */
const rows = computed(() =>
  SENSOR_TYPES.map((type) => {
    const sensor = props.sensors.find((s) => s.type === type.type)
    const series = sensor && props.data?.series.find((x) => x.sensorId === sensor.id)
    return sensor && series ? { type, sensor, series } : null
  }).filter(Boolean),
)

/** 이 설비에 없는 센서 종류 이름 ('CO' 등) */
const missing = computed(() =>
  SENSOR_TYPES.filter((t) => !props.sensors.some((s) => s.type === t.type)).map((t) => t.label),
)
</script>

<template>
  <BaseCard title="센서 추이" :sub="RANGE_LABEL[range]">
    <template #actions>
      <SegButtons v-model="pick" :options="RANGES" small />
      <button type="button" class="switch" role="switch" :aria-checked="showPrev" @click="showPrev = !showPrev">
        전날 같이 보기<span class="tg" :class="{ on: showPrev }" />
      </button>
      <span class="zoom">
        <button type="button" title="축소" @click="zoomBy(-1)"><AppIcon name="minus" :size="14" /></button>
        <button type="button" title="전체 보기" @click="zoomBy(0)">전체</button>
        <button type="button" title="확대" @click="zoomBy(1)"><AppIcon name="plus" :size="14" /></button>
      </span>
      <span class="legend"><i class="prev" />전날</span>
      <span class="legend"><i class="warn" />주의 기준</span>
      <span class="legend"><i class="alarm" />경보 기준</span>
    </template>

    <div class="rows" :class="{ loading }">
      <!-- 센서가 하나도 없는 설비 -->
      <div v-if="!sensors.length" class="empty">
        <AppIcon name="chart" :size="24" />
        <b>연결된 센서가 없습니다</b>
        <span>공장 관리에서 센서를 연결해 주세요.</span>
      </div>

      <template v-else>
        <TrendRow
          v-for="r in rows"
          :key="`${r.sensor.id}-${range}`"
          :type="r.type"
          :sensor="r.sensor"
          :series="r.series"
          :live-value="liveValues[r.sensor.id] ?? null"
          :show-prev="showPrev"
          :whole-day="range === 'today'"
          :zoom="zoom"
          :group="GROUP"
          @zoom="zoom = $event"
        />
        <!-- 일부 종류만 있는 설비: 남은 자리에 안내 -->
        <div v-if="missing.length" class="empty partial">
          <AppIcon name="info" :size="24" />
          <b>{{ missing.join(', ') }} 센서는 이 설비에 연결되지 않았습니다</b>
          <span>공장 관리에서 센서를 연결하면 추이가 여기에 표시됩니다.</span>
        </div>
      </template>
    </div>
  </BaseCard>
</template>

<style scoped>
.rows {
  height: 438px;
  display: flex;
  flex-direction: column;
  transition: opacity var(--m-value);
}

/* 범위를 바꿔 다시 불러오는 동안 흐리게 */
.rows.loading {
  opacity: 0.45;
}

/* '전날 같이 보기' 스위치 (설계안 .tg) */
.switch {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: none;
  background: none;
  font-size: 13px;
  color: var(--color-text-sub);
  cursor: pointer;
}

.tg {
  position: relative;
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: var(--color-border-strong);
  transition: background 200ms;
}

.tg::after {
  content: '';
  position: absolute;
  left: 2px;
  top: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: left 200ms var(--ease-out);
}

.tg.on {
  background: var(--color-primary);
}

.tg.on::after {
  left: 18px;
}

/* 확대 단추 묶음 (설계안 .zmb) */
.zoom {
  display: inline-flex;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.zoom button {
  display: grid;
  place-items: center;
  min-width: 30px;
  height: 28px;
  padding: 0 8px;
  border: none;
  border-right: 1px solid var(--color-border);
  background: #fff;
  font-size: 12px;
  color: var(--color-text-sub);
  cursor: pointer;
  transition: background var(--m-hover);
}

.zoom button:last-child {
  border-right: none;
}

.zoom button:hover {
  background: #f1f5f9;
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 범례: 점선 */
.legend i {
  width: 16px;
  border-top: 2px dashed;
}

.legend .prev {
  border-color: #94a3b8;
}

.legend .warn {
  border-color: var(--color-level-warn);
}

.legend .alarm {
  border-color: var(--color-level-alarm);
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
  text-align: center;
}

.empty b {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.empty.partial {
  border-top: 1px solid #f1f5f9;
}
</style>
