<!--
  요약 4칸 (화면설계안 vStats() K 배열)
  - 전체 알림 / 경보 / 평균 조치 시간 / 체크리스트 완료율
  - 칸마다 직전 같은 길이 기간 대비 증감 (줄면 초록 ▼, 늘면 빨강 ▲ — 완료율은 반대)
  - 오른쪽 작은 그림: 추이 선(전체·평균 조치), 막대(경보), 고리(완료율)
  - 조회할 때마다 숫자가 이전 값에서 새 값으로 0.4초 (--m-value)
-->
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import ProgressRing from '@/components/ProgressRing.vue'

const props = defineProps({
  /** GET /stats/summary */
  summary: { type: Object, default: null },
  /** GET /stats/trend 의 points */
  points: { type: Array, default: () => [] },
  /** 오늘이면 '어제보다', 아니면 '이전 기간보다' */
  today: { type: Boolean, default: false },
})

const cmp = computed(() => (props.today ? '어제보다' : '이전 기간보다'))

/* ───────── 숫자 0.4초 바뀜 ───────── */
const shown = ref([0, 0, 0, 0])
let raf = 0
function tween(target) {
  cancelAnimationFrame(raf)
  const from = [...shown.value]
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  const start = performance.now()
  const step = (t) => {
    const k = reduce ? 1 : Math.min(1, (t - start) / 400)
    const ease = 1 - (1 - k) ** 3
    shown.value = target.map((v, i) => from[i] + ((v ?? 0) - from[i]) * ease)
    if (k < 1) raf = requestAnimationFrame(step)
  }
  raf = requestAnimationFrame(step)
}
onBeforeUnmount(() => cancelAnimationFrame(raf))

const values = computed(() => {
  const s = props.summary
  return s ? [s.alarmCount.value, s.dangerCount.value, s.avgActionMinutes.value, s.checklistDoneRate.value] : [0, 0, null, null]
})
watch(values, (v) => tween(v), { immediate: true })

/** 증감 한 줄: good = 줄어드는 게 좋은 값이면 'down' */
function diffLine(metric, unit, good = 'down') {
  const d = metric?.diff
  if (d == null || d === 0) return { text: d === 0 ? `${cmp.value} 같음` : '', tone: '' }
  const up = d > 0
  const isGood = good === 'down' ? !up : up
  return { text: `${up ? '▲' : '▼'} ${Math.abs(d)}${unit}`, tone: isGood ? 'good' : 'bad' }
}

const cards = computed(() => {
  const s = props.summary
  const n = s?.alarmCount.value ?? 0
  const has = n > 0
  return [
    { icon: 'bell', label: '전체 알림', unit: '건', digits: 0, empty: !has, emptyText: '알림 없음', diff: diffLine(s?.alarmCount, '건') },
    { icon: 'alert', label: '경보', unit: '건', digits: 0, red: (s?.dangerCount.value ?? 0) > 0, empty: !s?.dangerCount.value, emptyText: '경보 없음', diff: diffLine(s?.dangerCount, '건') },
    { icon: 'clock', label: '평균 조치 시간', unit: '분', digits: 0, empty: s?.avgActionMinutes.value == null, emptyText: '집계할 알림 없음', diff: diffLine(s?.avgActionMinutes, '분') },
    {
      icon: 'clip',
      label: '체크리스트 완료율',
      unit: '%',
      digits: 1,
      empty: s?.checklistDoneRate.value == null,
      emptyText: '집계할 알림 없음',
      // 응답 추가 필드 checklistNotDoneCount 가 있으면 '미완료 n건', 없으면 증감
      note: s?.checklistNotDoneCount != null ? `미완료 ${s.checklistNotDoneCount}건` : '',
      diff: diffLine(s?.checklistDoneRate, '%p', 'up'),
    },
  ]
})

/* ───────── 작은 그림 (170 × 48) ───────── */
const W = 170
const H = 48
/** 값 목록 → 선 좌표 */
function linePath(list) {
  const v = list.filter((x) => x != null)
  if (v.length < 2) return ''
  const max = Math.max(...v, 1)
  return v.map((y, i) => `${(i / (v.length - 1)) * W},${H - 4 - (y / max) * (H - 8)}`).join(' ')
}
const totalLine = computed(() => linePath(props.points.map((p) => p.warn + p.alarm)))
const actionLine = computed(() => linePath(props.points.map((p) => p.avgActionMinutes ?? null)))
const alarmBars = computed(() => {
  const v = props.points.map((p) => p.alarm)
  const max = Math.max(...v, 1)
  const w = W / Math.max(v.length, 1)
  return v.map((y, i) => ({ x: i * w + w * 0.15, w: w * 0.7, h: (y / max) * (H - 6) }))
})

const fmtNum = (v, digits) => (digits ? v.toFixed(digits) : Math.round(v))
</script>

<template>
  <div class="kpis">
    <BaseCard v-for="(c, i) in cards" :key="c.label" class="kpi">
      <div class="lb"><AppIcon :name="c.icon" :size="16" />{{ c.label }}</div>
      <div class="v mono" :class="{ red: c.red, weak: c.empty }">
        <template v-if="c.empty && i >= 2">–</template>
        <template v-else>{{ fmtNum(shown[i], c.digits) }}<small>{{ c.unit }}</small></template>
      </div>
      <div class="d">
        <template v-if="c.empty">{{ c.emptyText }}</template>
        <template v-else-if="c.note">{{ c.note }}</template>
        <template v-else-if="c.diff.text">
          {{ cmp }} <b class="mono" :class="c.diff.tone">{{ c.diff.text }}</b>
        </template>
      </div>
      <!-- 오른쪽 작은 그림 -->
      <div v-if="!c.empty" class="viz">
        <svg v-if="i === 0 && totalLine" :width="W" :height="H" aria-hidden="true">
          <polyline :points="totalLine" fill="none" stroke="var(--color-primary)" stroke-width="2" stroke-linejoin="round" />
        </svg>
        <svg v-else-if="i === 1" :width="W" :height="H" aria-hidden="true">
          <rect v-for="(b, j) in alarmBars" :key="j" :x="b.x" :y="H - b.h" :width="b.w" :height="b.h" rx="1" fill="var(--color-level-alarm)" />
        </svg>
        <svg v-else-if="i === 2 && actionLine" :width="W" :height="H" aria-hidden="true">
          <polyline :points="actionLine" fill="none" stroke="#0d9488" stroke-width="2" stroke-linejoin="round" />
        </svg>
        <ProgressRing v-else-if="i === 3" :value="summary.checklistDoneRate.value" :total="100" :size="64" :stroke="8" color="var(--color-level-normal)" />
      </div>
    </BaseCard>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi {
  position: relative;
  height: 116px;
  padding: 15px 20px 12px;
  min-width: 0;
}

.lb {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text-sub);
}

.lb svg {
  color: var(--color-text-weak);
}

.v {
  margin-top: 6px;
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.5px;
  line-height: 1.2;
}

.v small {
  margin-left: 4px;
  font-family: var(--font-sans, inherit);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-weak);
  letter-spacing: 0;
}

.v.red {
  color: var(--color-level-alarm);
}

.v.weak {
  color: var(--color-text-weak);
}

.d {
  position: absolute;
  left: 20px;
  bottom: 12px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.d b {
  font-weight: 600;
}

.good {
  color: var(--color-level-normal-text);
}

.bad {
  color: var(--color-level-alarm-text);
}

.viz {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
}

@media (max-width: 1500px) {
  .viz {
    display: none;
  }
}
</style>
