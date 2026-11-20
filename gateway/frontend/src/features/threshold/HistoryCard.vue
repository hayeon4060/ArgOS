<!--
  '변경 이력' 카드 (화면설계안 vThreshold() history())
  - 위: 조회 기간 (시작일 ~ 종료일 + 조회)
  - 그래프: 고르고 있는 센서의 주의 기준 변화 (8월 · 9월 · 10월 · 11월 · 지금, 계단 모양) — 바꾸는 중이면 파란 점선으로 다음 값
  - 목록: 저장 전(파란 '저장 전' 배지) 줄이 맨 위, 그 아래 저장된 변경 (시각 · 바꾼 사람 · 무엇을 · 이전 → 이후 · 사유)
    줄이 차례로 오른쪽에서 들어옴(.4초, 55ms 간격)
  - 아래: '저장하면 바로 적용되고, 바꾼 사람과 시각이 이력에 남습니다.'
-->
<script setup>
import { computed, ref } from 'vue'
import { fmt, label } from './thresholdForm'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import { CHART_COLORS } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'

const props = defineProps({
  equipmentName: { type: String, required: true },
  /** 설비 센서 목록 (태그 → 종류·단위) */
  sensors: { type: Array, required: true },
  /** GET /thresholds/history */
  rows: { type: Array, required: true },
  /** 저장 전 바꾼 값 [{ sensor, field, before, after }] */
  pending: { type: Array, default: () => [] },
  /** 그래프에 보여 줄 센서 */
  sensor: { type: Object, default: null },
  /** 그 센서의 지금 주의 기준(저장된 값)과 입력 중인 값 */
  savedWarn: { type: Number, default: null },
  nextWarn: { type: Number, default: null },
  /** 조회 기간 { from, to } 'YYYY-MM-DD' */
  range: { type: Object, required: true },
  userLabel: { type: String, default: '' },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['query'])

const from = ref(props.range.from)
const to = ref(props.range.to)

const sensorOf = (tag) => props.sensors.find((s) => s.tag === tag)
const fieldOf = (r) => (r.before && r.before.warn !== r.after.warn ? 'warn' : 'alarm')
const dotColor = (r) => (!r.before ? '#94a3b8' : fieldOf(r) === 'warn' ? CHART_COLORS.warn : CHART_COLORS.alarm)
const when = (iso) => `${iso.slice(5, 10)} ${iso.slice(11, 16)}`

/* ───────── 그래프: 월말마다 그 센서의 주의 기준 ───────── */
const MONTH_ENDS = [
  ['8월', '2026-08-31'],
  ['9월', '2026-09-30'],
  ['10월', '2026-10-31'],
  ['11월', '2026-11-30'],
]
const option = computed(() => {
  const s = props.sensor
  if (!s || props.savedWarn == null) return null
  const mine = props.rows.filter((r) => r.sensorId === s.sensorId).sort((a, b) => a.changedAt.localeCompare(b.changedAt))
  const at = (day) => [...mine].reverse().find((r) => r.changedAt.slice(0, 10) <= day)?.after.warn ?? mine[0]?.after.warn ?? props.savedWarn
  const base = MONTH_ENDS.map(([, d]) => at(d))
  const cur = props.savedWarn
  const nv = props.nextWarn ?? cur
  const all = [...base, cur, nv]
  const lo = Math.min(...all)
  const hi = Math.max(...all)
  const pad = Math.max((hi - lo) * 0.5, cur * 0.06)
  const labels = [...MONTH_ENDS.map(([m]) => m), '지금']
  return {
    ...chartMotion(),
    grid: { left: 48, right: 30, top: 18, bottom: 26 },
    xAxis: { type: 'category', data: labels, boundaryGap: false, axisTick: { show: false }, axisLine: { lineStyle: { color: CHART_COLORS.grid } }, axisLabel: { color: CHART_COLORS.axis, fontSize: 12 } },
    yAxis: {
      type: 'value',
      min: Number((lo - pad).toFixed(1)),
      max: Number((hi + pad).toFixed(1)),
      splitNumber: 4,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: CHART_COLORS.grid } },
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11 },
    },
    series: [
      { type: 'line', step: 'end', data: [...base, cur], symbol: 'circle', symbolSize: 6, lineStyle: { color: CHART_COLORS.warn, width: 2 }, itemStyle: { color: CHART_COLORS.warn } },
      ...(nv !== cur
        ? [
            {
              type: 'line',
              // 11월(저장된 값) → 지금(입력 중인 값) 파란 점선 (설계안과 같음)
              data: [null, null, null, cur, nv],
              symbol: 'circle',
              symbolSize: 8,
              lineStyle: { color: CHART_COLORS.action, width: 2, type: 'dashed' },
              itemStyle: { color: CHART_COLORS.action },
              label: { show: true, formatter: fmt(nv), color: '#1d4ed8', fontFamily: 'Fira Code', fontWeight: 600, fontSize: 12, position: 'left' },
            },
          ]
        : []),
    ],
  }
})

function query() {
  if (from.value > to.value) {
    emit('query', null) // 페이지가 '시작일이 종료일보다 늦습니다' 안내
    return
  }
  emit('query', { from: from.value, to: to.value })
}
</script>

<template>
  <BaseCard title="변경 이력" :sub="equipmentName" class="hist-card">
    <div class="hdf">
      <input v-model="from" type="date" class="dti mono" aria-label="조회 시작일" />
      <span class="tilde">~</span>
      <input v-model="to" type="date" class="dti mono" aria-label="조회 종료일" />
      <button type="button" class="btn" @click="query">조회</button>
    </div>

    <template v-if="sensor">
      <div class="ct">
        {{ label(sensor) }} 주의 기준 변화<span>단위 {{ sensor.unit }}</span>
      </div>
      <VChart v-if="option" class="chart" :option="option" autoresize />
    </template>

    <div class="list" :class="{ busy }">
      <template v-if="pending.length || rows.length">
        <div v-for="(p, i) in pending" :key="`p-${p.sensor.sensorId}-${p.field}`" class="hi pop-in" :style="{ '--j': i }">
          <span class="d" :style="{ borderColor: 'var(--color-primary)' }" />
          <div class="m"><span class="mono">지금</span>·<b>{{ userLabel }}</b><span class="m-chip info right">저장 전</span></div>
          <div class="c">
            {{ label(p.sensor) }} {{ p.field === 'warn' ? '주의' : '경보' }}
            <s class="mono">{{ fmt(p.before) }}</s><AppIcon name="arrow" :size="14" class="ar" />
            <span class="mono">{{ p.after != null ? `${fmt(p.after)} ${p.sensor.unit}` : '–' }}</span>
          </div>
        </div>
        <div v-for="(r, i) in rows" :key="r.id" class="hi go" :style="{ '--j': i + pending.length }">
          <span class="d" :style="{ borderColor: dotColor(r) }" />
          <div class="m"><span class="mono">{{ when(r.changedAt) }}</span>·<b>{{ r.changedBy }}</b></div>
          <div v-if="r.before" class="c">
            {{ label(sensorOf(r.sensorTag) ?? { type: '' }) }} {{ fieldOf(r) === 'warn' ? '주의' : '경보' }}
            <s class="mono">{{ fmt(r.before[fieldOf(r)]) }}</s><AppIcon name="arrow" :size="14" class="ar" />
            <span class="mono">{{ fmt(r.after[fieldOf(r)]) }} {{ sensorOf(r.sensorTag)?.unit }}</span>
          </div>
          <div v-else class="c">처음 등록 <span class="mono tag">{{ r.sensorTag }}</span></div>
          <div class="w">{{ r.reason }}</div>
        </div>
      </template>
      <div v-else class="empty">
        <AppIcon name="clock" :size="22" />
        <b>이 기간에 바꾼 기록이 없습니다</b>
        조회 기간을 넓혀 보세요.
      </div>
    </div>

    <div class="nt"><AppIcon name="info" :size="15" /><span>저장하면 바로 적용되고, 바꾼 사람과 시각이 이력에 남습니다.</span></div>
  </BaseCard>
</template>

<style scoped>
.hist-card {
  display: flex;
  flex-direction: column;
}

/* 조회 기간 (설계안 .hdf) */
.hdf {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px 0;
}

.dti {
  flex: 1;
  width: 0;
  height: 34px;
  padding: 0 6px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 12px;
  outline: none;
}

.dti:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.tilde {
  color: var(--color-text-weak);
}

.hdf .btn {
  height: 34px;
  padding: 0 12px;
}

.ct {
  display: flex;
  padding: 12px 20px 0;
  font-size: 13.5px;
  font-weight: 600;
}

.ct span {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-weak);
}

.chart {
  flex: none;
  height: 180px;
}

.list {
  flex: 1;
  min-height: 0; /* 카드 높이는 기준값 관리 창이 정함(센서 카드 줄에 맞춤) → 남는 만큼만 차지하고 길면 스크롤 */
  overflow-y: auto;
  padding: 10px 20px 0;
  transition: opacity var(--m-value);
}

.list.busy {
  opacity: 0.5;
}

/* 이력 한 줄 (설계안 .hi) */
.hi {
  position: relative;
  padding: 0 0 26px 26px;
}

/* 차례로 오른쪽에서 들어옴 (설계안 .go .hi slidein) */
.hi.go {
  animation: hi-slide 400ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
  animation-delay: calc(var(--j, 0) * 55ms);
}

.hi::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 16px;
  bottom: -4px;
  width: 2px;
  background: var(--color-border);
}

.hi:last-child::before {
  display: none;
}

.d {
  position: absolute;
  left: 0;
  top: 3px;
  width: 14px;
  height: 14px;
  border: 3px solid;
  border-radius: 50%;
  background: #fff;
}

.m {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.m b {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.m .right {
  margin-left: auto;
}

.c {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
  font-size: 14px;
  font-weight: 600;
}

.c s {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.ar {
  color: var(--color-text-weak);
}

.tag {
  font-size: 12px;
  color: var(--color-text-weak);
}

.w {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.empty {
  height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.empty b {
  font-size: 14px;
  color: var(--color-text-sub);
}

.nt {
  display: flex;
  gap: 8px;
  margin: 12px 20px 20px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: #f8fafc;
  font-size: 12.5px;
  line-height: 19px;
  color: var(--color-text-sub);
}

.nt .app-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-text-weak);
}

@keyframes hi-slide {
  from {
    opacity: 0;
    transform: translateX(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hi.go {
    animation-name: softin;
    animation-delay: 0s;
  }
}
</style>
