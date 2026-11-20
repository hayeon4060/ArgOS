<!--
  '변경 시뮬레이션' 카드 (화면설계안 vThreshold() preview(), 명세서 F1 변경 미리보기)
  - 지금 고치는 센서·칸(주의 또는 경보)에 대해: 현재 기준 알림 / 변경 후 알림 / 차이 (최근 7일)
  - 오른쪽 문장: 늘면 '… 2건 더 발생합니다'(주황), 줄면 '… 1건 줄어듭니다'(초록), 같으면 그대로
  - 아래 그래프: 최근 7일 값과 기준선, 넘었던 순간 점 (simulationOption.js)
  - 숫자는 POST /thresholds/simulate (입력이 멈추고 0.4초 뒤 페이지가 부름)
-->
<script setup>
import { computed } from 'vue'
import { TYPE_COLOR, peaksOf, simulationOption } from './simulationOption'
import { fmt, label } from './thresholdForm'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  sensor: { type: Object, required: true },
  field: { type: String, required: true },
  saved: { type: Object, required: true },
  /** 입력 중 기준 { warn, alarm } (숫자) */
  next: { type: Object, required: true },
  /** 최근 7일 값 (불러오기 전이면 null) */
  series: { type: Object, default: null },
  /** POST /thresholds/simulate 결과 (불러오기 전이면 null) */
  sim: { type: Object, default: null },
  busy: { type: Boolean, default: false },
})

const name = computed(() => (props.field === 'warn' ? '주의' : '경보'))
const before = computed(() => props.saved[props.field])
const after = computed(() => props.next[props.field])
const changed = computed(() => after.value !== before.value)
const countKey = computed(() => (props.field === 'warn' ? 'warnCount' : 'alarmCount'))
const n0 = computed(() => props.sim?.current[countKey.value] ?? null)
const n1 = computed(() => props.sim?.proposed[countKey.value] ?? null)
const diff = computed(() => (n0.value == null || n1.value == null ? null : n1.value - n0.value))

const sub = computed(() =>
  changed.value
    ? `${label(props.sensor)} ${name.value} 기준을 ${fmt(after.value)} ${props.sensor.unit}로 ${after.value < before.value ? '낮췄을' : '높였을'} 때 최근 7일 ${props.field === 'warn' ? '알림' : '경보'}`
    : `${label(props.sensor)} ${props.sensor.tag} · 최근 7일`,
)

const option = computed(() =>
  props.series && props.sim
    ? simulationOption({
        series: props.series,
        peaks: peaksOf(props.sim, props.series), // peaks 또는 hits(지금 백엔드) 둘 다 받음
        type: props.sensor.type,
        unit: props.sensor.unit,
        field: props.field,
        saved: props.saved,
        next: props.next,
      })
    : null,
)
</script>

<template>
  <BaseCard title="변경 시뮬레이션" :sub="sub">
    <template #actions>
      <span class="lg"><i :style="{ background: TYPE_COLOR[sensor.type] }" class="solid" />{{ label(sensor) }} {{ sensor.tag }}</span>
      <template v-if="changed">
        <span class="lg"><i class="dash gray" />현재 {{ name }} {{ fmt(before) }}</span>
        <span class="lg"><i class="dash blue" />변경 {{ name }} {{ fmt(after) }}</span>
      </template>
      <span v-else class="lg"><i class="dash warn" />주의 {{ fmt(next.warn) }}</span>
      <span class="lg"><i class="dash alarm" />경보 {{ fmt(next.alarm) }}</span>
    </template>

    <div class="body" :class="{ busy }">
      <div class="pvs">
        <div class="pv">현재 기준 알림<b class="mono">{{ n0 ?? '–' }}<small v-if="n0 != null">건</small></b></div>
        <div class="pv" :class="{ on: changed }">
          변경 후 알림<b class="mono blue">{{ changed && n1 != null ? n1 : '–' }}<small v-if="changed && n1 != null">건</small></b>
        </div>
        <div class="pv">
          차이
          <b class="mono" :class="{ up: changed && diff > 0, down: changed && diff < 0 }">
            {{ changed && diff != null ? (diff > 0 ? `+${diff}` : diff) : '–' }}<small v-if="changed && diff != null">건</small>
          </b>
        </div>
        <p class="say">
          <template v-if="!changed">주의나 경보 기준을 바꾸면, 최근 7일 기록에 적용했을 때 알림이 몇 건 달라지는지 여기서 미리 볼 수 있습니다.</template>
          <template v-else-if="diff > 0">
            기준을 {{ after < before ? '낮추면' : '바꾸면' }} 지난 7일 동안 {{ name }}가 <b class="up">{{ diff }}건 더</b> 발생합니다.<br />
            {{ field === 'warn' ? `공정 담당자에게 가는 체크리스트도 ${diff}건 늘어납니다.` : '경보가 늘면 설비 정지 판단도 그만큼 늘어납니다.' }}
          </template>
          <template v-else-if="diff < 0">
            기준을 높이면 지난 7일 동안 {{ name }}가 <b class="down">{{ -diff }}건 줄어듭니다.</b><br />
            이상을 늦게 알아챌 수 있으니 평소값과의 차이를 확인해 주세요.
          </template>
          <template v-else>이 값으로 바꿔도 지난 7일 동안의 알림 건수는 그대로입니다.</template>
        </p>
      </div>
      <VChart v-if="option" class="chart" :option="option" autoresize />
      <div v-else class="chart wait">불러오는 중</div>
    </div>
  </BaseCard>
</template>

<style scoped>
.body {
  transition: opacity var(--m-value);
}

/* 다른 센서 값을 불러오는 동안 흐리게 */
.body.busy {
  opacity: 0.55;
}

.pvs {
  display: flex;
  gap: 12px;
  padding: 16px 20px 0;
}

/* 숫자 상자 (설계안 .pv) */
.pv {
  width: 158px;
  height: 74px;
  flex: none;
  padding: 11px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  color: var(--color-text-sub);
  transition:
    border-color 200ms,
    background 200ms;
}

.pv.on {
  border-color: #93c5fd;
  background: var(--color-primary-soft);
}

.pv b {
  display: block;
  margin-top: 3px;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
}

.pv b small {
  margin-left: 3px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.pv b.blue {
  color: var(--color-primary-hover);
}

.up {
  color: var(--color-level-warn-text) !important;
}

.down {
  color: var(--color-level-normal-text) !important;
}

.say {
  align-self: center;
  margin-left: 8px;
  font-size: 13px;
  line-height: 21px;
  color: var(--color-text-sub);
}

.chart {
  height: 296px;
}

.chart.wait {
  display: grid;
  place-items: center;
  color: var(--color-text-weak);
}

/* 범례 */
.lg {
  display: flex;
  align-items: center;
  gap: 6px;
}

.lg i {
  width: 16px;
}

.lg .solid {
  height: 3px;
}

.lg .dash {
  border-top: 2px dashed;
}

.dash.gray {
  border-color: #94a3b8;
}

.dash.blue {
  border-color: var(--color-primary);
}

.dash.warn {
  border-color: var(--color-level-warn);
}

.dash.alarm {
  border-color: var(--color-level-alarm);
}
</style>
