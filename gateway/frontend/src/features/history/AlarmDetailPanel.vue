<!--
  알림 상세 패널 (2026-11-19 개편: 알림 이력의 '상세'를 누르면 오른쪽 두 카드 위를 덮음)
  - 머리: 등급·유형·설비 이름, 발생·해제 시각, 조치 시간, 닫기(X · Esc)
  - 센서 측정값: 그 설비 센서 3종(전류·온도·CO), 처음 기준을 넘은 시각 ~ 조치 완료 (조치 중이면 지금까지)
  - 열화상 1장(발생 때) · 조치 이력 · 체크리스트(주의) 또는 셧다운 단계(경보)
  - 아래: 센서값 내보내기(CSV, 바탕화면 저장 창) · 설비 상세 화면
  - 데이터: GET /stats/alarms/{alarmId} 한 번 (docs/stats-history-api.md)
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { sensorSeriesOption } from './historyCharts'
import { HAZARD_META, SENSOR_ICON, SENSOR_LABEL, placeOf } from './historyFilter'
import { sensorCsvBlob, sensorCsvName } from './sensorCsv'
import { getStatsAlarmDetail } from '@/api/stats'
import { useThermalSrc } from '@/features/equipment/useThermalSrc'
import { saveFile } from '@/utils/saveFile'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'

const props = defineProps({
  /** 보여 줄 알림 id */
  alarmId: { type: Number, required: true },
})

const emit = defineEmits(['close', 'equipment'])

const detail = ref(null)
const busy = ref(false)
const bodyEl = ref(null)
let seq = 0 // 빠르게 다른 줄을 눌렀을 때 늦게 온 이전 응답은 버림

async function load() {
  const my = ++seq
  busy.value = true
  try {
    const d = await getStatsAlarmDetail(props.alarmId)
    if (my !== seq) return
    detail.value = d
    if (bodyEl.value) bodyEl.value.scrollTop = 0 // 다른 알림을 열면 맨 위부터
  } catch (error) {
    if (my === seq) toast(error.message, { type: 'err' })
  } finally {
    if (my === seq) busy.value = false
  }
}
watch(() => props.alarmId, load, { immediate: true })

/* ───────── 화면용 값 ───────── */
const hhmmss = (iso) => (iso ? iso.slice(11, 19) : '')
const mmdd = (iso) => (iso ? iso.slice(5, 10) : '')
const hhmm = (iso) => (iso ? iso.slice(11, 16) : '')
const LEVEL_NAME = { WARN: '주의', ALARM: '경보' }

const place = computed(() => placeOf(detail.value?.location))
// 열화상 사진 (서버 사진은 로그인이 필요해 blob: 주소로 받아 씀, 없거나 실패하면 '열화상 없음')
const thermalSrc = useThermalSrc(() => detail.value?.thermal?.imageUrl)
const hazard = computed(() => HAZARD_META[detail.value?.hazard] ?? null)
const active = computed(() => detail.value && detail.value.status !== 'CLOSED')
const cause = computed(() => {
  const d = detail.value
  if (!d) return ''
  return `${place.value.equipment} ${SENSOR_LABEL[d.sensorType]}이(가) ${LEVEL_NAME[d.level]} 기준(${d.threshold} ${d.unit})을 넘었습니다. 발생 값 ${d.value} ${d.unit}.`
})
/** 센서 3종: 칸 왼쪽 이름·최고값 (최고값 색 = 그 값이 닿은 단계) */
const sensors = computed(() =>
  (detail.value?.series.sensors ?? []).map((s) => {
    const max = Math.max(...s.points.map((p) => p[1]))
    return { ...s, max, level: max >= s.alarm ? 'alarm' : max >= s.warn ? 'warn' : 'normal' }
  }),
)
const ROW = 92 // 센서 칸 하나 높이
const option = computed(() => (detail.value ? sensorSeriesOption(detail.value.series, ROW) : null))
const chartHeight = computed(() => sensors.value.length * (ROW + 14) + 24)
const rangeText = computed(() => {
  const s = detail.value?.series
  if (!s) return ''
  const step = s.intervalSec < 60 ? `${s.intervalSec}초 간격` : '1분 평균'
  return `${hhmmss(s.from)} 처음 넘음 ~ ${active.value ? '지금' : `${hhmmss(s.to)} 조치 완료`} · ${step}`
})

/* ───────── 단추 ───────── */
const saving = ref(false)
async function exportSensors() {
  const d = detail.value
  if (!d || saving.value) return
  saving.value = true
  try {
    const name = sensorCsvName(d)
    const result = await saveFile(name, async () => sensorCsvBlob(d))
    if (result === 'saved') toast(`${name} 파일을 저장했습니다.`)
    if (result === 'downloaded') toast(`${name} 파일을 다운로드 폴더에 저장했습니다.`)
  } catch (error) {
    toast(`저장하지 못했습니다. ${error.message}`, { type: 'err' })
  } finally {
    saving.value = false
  }
}

// Esc 로 닫기
const onKey = (e) => e.key === 'Escape' && emit('close')
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <section class="panel" role="dialog" aria-labelledby="alarm-detail-title">
    <header class="head">
      <template v-if="detail">
        <div class="title">
          <LevelBadge :level="detail.level" />
          <span v-if="hazard" class="hz" :class="detail.hazard"><AppIcon :name="hazard.icon" :size="14" />{{ hazard.label }}</span>
          <h3 id="alarm-detail-title">{{ place.equipment }}</h3>
          <span class="proc">{{ place.factory }} · {{ place.process }}</span>
        </div>
        <div class="when">
          <span>발생 <b class="mono">{{ mmdd(detail.raisedAt) }} {{ hhmmss(detail.raisedAt) }}</b></span>
          <span class="sep">|</span>
          <span v-if="active">조치 중</span>
          <span v-else>해제 <b class="mono">{{ hhmmss(detail.closedAt) }}</b></span>
          <span class="sep">|</span>
          <span>조치 시간 <b class="mono">{{ detail.actionMinutes }}분{{ active ? ' 경과' : '' }}</b></span>
          <span class="sep">|</span>
          <span>알림 번호 <b class="mono">#{{ detail.id }}</b></span>
        </div>
      </template>
      <h3 v-else id="alarm-detail-title">알림 상세</h3>
      <button type="button" class="x" aria-label="상세 닫기" @click="emit('close')"><AppIcon name="x" :size="18" /></button>
    </header>

    <div v-if="detail" ref="bodyEl" class="body" :class="{ busy }">
      <p class="cause" :class="detail.level">{{ cause }}</p>

      <!-- 센서 3종 측정값 -->
      <section>
        <h4>센서 측정값 <small>{{ rangeText }}</small></h4>
        <div class="sens">
          <ul class="names" :style="{ gridTemplateRows: `repeat(${sensors.length}, ${ROW + 14}px)` }">
            <li v-for="s in sensors" :key="s.tag">
              <span class="s-top"><AppIcon :name="SENSOR_ICON[s.type]" :size="14" />{{ SENSOR_LABEL[s.type] }}<span class="mono">{{ s.tag }}</span></span>
              <span class="s-max">최고 <b class="mono" :class="s.level">{{ s.max }} {{ s.unit }}</b></span>
              <span v-if="s.trigger" class="trig">알림 센서</span>
            </li>
          </ul>
          <VChart class="chart" :style="{ height: `${chartHeight}px` }" :option="option" autoresize />
        </div>
      </section>

      <!-- 열화상 | 조치 이력 -->
      <section class="two">
        <div>
          <h4>열화상 <small>{{ hhmm(detail.thermal?.capturedAt) }} 촬영 · {{ detail.thermal?.cameraTag }}</small></h4>
          <div v-if="thermalSrc" class="ir">
            <img :src="thermalSrc" :alt="`${place.equipment} 열화상`" />
            <i v-if="detail.thermal.maxPoint" class="pt" :style="{ left: `${detail.thermal.maxPoint.x * 100}%`, top: `${detail.thermal.maxPoint.y * 100}%` }" />
          </div>
          <div v-else class="ir none">열화상 없음</div>
          <div v-if="detail.thermal" class="ir-num">
            <span>최고 <b class="mono">{{ detail.thermal.max }} ℃</b></span>
            <span>평균 <b class="mono">{{ detail.thermal.avg }} ℃</b></span>
          </div>
        </div>
        <div>
          <h4>조치 이력</h4>
          <ul class="tl">
            <li v-for="(t, i) in detail.timeline" :key="i" :class="t.step">
              <i /><span class="mono at">{{ hhmmss(t.at) }}</span><b>{{ t.text }}</b><span v-if="t.by" class="by"> · {{ t.by }}</span>
            </li>
            <li v-if="active" class="WAIT"><i /><span class="mono at">진행 중</span>완료 보고를 기다리는 중</li>
          </ul>
        </div>
      </section>

      <!-- 체크리스트 (주의) -->
      <section v-if="detail.checklist">
        <h4>체크리스트 <small>{{ detail.checklist.assignee ?? '공정 담당자' }} · {{ detail.checklist.doneCount }}/{{ detail.checklist.totalCount }} 완료</small></h4>
        <ul class="ck">
          <li v-for="it in detail.checklist.items" :key="it.seq" :class="{ done: it.doneAt }">
            <span class="box"><AppIcon v-if="it.doneAt" name="check" :size="12" /></span>
            <span>
              {{ it.content }}
              <span v-if="it.memo" class="memo">현장 메모 · {{ it.memo }}</span>
            </span>
            <span class="res">{{ it.doneAt ? it.result : '대기' }} <span v-if="it.doneAt" class="mono t">{{ hhmm(it.doneAt) }}</span></span>
          </li>
        </ul>
      </section>

      <!-- 셧다운 단계 (경보) -->
      <section v-else-if="detail.shutdown">
        <h4>셧다운 단계</h4>
        <ul class="ck">
          <li v-for="st in detail.shutdown.steps" :key="st.label" :class="{ done: st.at }">
            <span class="box"><AppIcon v-if="st.at" name="check" :size="12" /></span>
            <span>{{ st.label }}</span>
            <span class="mono t">{{ hhmmss(st.at) }}</span>
          </li>
        </ul>
      </section>
    </div>
    <div v-else class="body empty">불러오는 중</div>

    <footer class="foot">
      <button type="button" class="btn" :disabled="!detail || saving" @click="exportSensors">
        <AppIcon name="download" :size="15" />센서값 내보내기
      </button>
      <button type="button" class="btn primary" :disabled="!detail" @click="emit('equipment', detail)">
        설비 상세 화면<AppIcon name="arrow" :size="15" />
      </button>
    </footer>
  </section>
</template>

<style scoped>
/* 오른쪽 열(일별 추이 + 공정별 알림) 위를 꽉 채워 덮음 — 위치는 HistoryPage 가 정함 */
.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid #cfd6df;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.head {
  position: relative;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 52px 12px 20px;
  border-bottom: 1px solid var(--color-border);
}

.title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

h3 {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.proc {
  font-size: 13px;
  color: var(--color-text-weak);
  white-space: nowrap;
}

.when {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.when b {
  font-weight: 500;
}

.when .sep {
  color: var(--color-border-strong);
}

.x {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-sub);
  cursor: pointer;
}

.x:hover {
  background: #f1f5f9;
  color: var(--color-text);
}

.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  transition: opacity 150ms;
}

/* 다른 알림을 불러오는 동안 살짝 흐리게 */
.body.busy {
  opacity: 0.4;
}

.body.empty {
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--color-text-weak);
}

h4 {
  margin-bottom: 8px;
  font-size: 13.5px;
  font-weight: 700;
}

h4 small {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-weak);
}

.cause {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-sub);
  font-size: 13.5px;
  line-height: 1.55;
}

.cause.WARN {
  border-left: 3px solid var(--color-level-warn);
}

.cause.ALARM {
  border-left: 3px solid var(--color-level-alarm);
}

/* 유형 칩 (알림 이력 표와 같은 모양) */
.hz {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 9px 0 7px;
  border-radius: 12px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
}

.hz.FIRE svg {
  color: #7c3aed;
}

.hz.GAS svg {
  color: #0d9488;
}

/* 센서 3종: 왼쪽 이름 줄 | 오른쪽 차트 (칸 높이를 차트와 맞춤) */
.sens {
  display: grid;
  grid-template-columns: 124px minmax(0, 1fr);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.names {
  list-style: none;
  display: grid;
  padding-top: 10px;
}

.names li {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 3px;
  padding-left: 12px;
  height: 92px;
}

.s-top {
  display: flex;
  white-space: nowrap;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
}

.s-top .mono {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.s-max {
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-sub);
}

.s-max b {
  font-weight: 600;
}

.s-max b.normal {
  color: var(--color-level-normal-text);
}

.s-max b.warn {
  color: var(--color-level-warn-text);
}

.s-max b.alarm {
  color: var(--color-level-alarm-text);
}

.trig {
  width: fit-content;
  padding: 1px 5px;
  border-radius: 3px;
  background: #f1f5f9;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.chart {
  width: 100%;
}

/* 열화상 | 조치 이력 */
.two {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
}

.ir {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: #1e1b4b;
}

.ir img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.ir.none {
  display: grid;
  place-items: center;
  background: var(--color-surface-sub);
  font-size: 12.5px;
  color: var(--color-text-weak);
}

/* 열화상 최고 온도 위치 */
.ir .pt {
  position: absolute;
  width: 18px;
  height: 18px;
  margin: -9px 0 0 -9px;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.35);
}

.ir-num {
  display: flex;
  gap: 14px;
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.ir-num b {
  font-weight: 600;
  color: var(--color-text);
}

.tl {
  list-style: none;
}

.tl li {
  position: relative;
  padding: 0 0 12px 22px;
  font-size: 13px;
  line-height: 1.45;
}

/* 점을 잇는 세로선 */
.tl li::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 7px;
  bottom: -7px;
  width: 1px;
  background: var(--color-border);
}

.tl li:last-child::before {
  display: none;
}

.tl li i {
  position: absolute;
  left: 0;
  top: 4px;
  width: 11px;
  height: 11px;
  border: 2px solid #94a3b8;
  border-radius: 50%;
  background: #fff;
}

.tl li.RAISED i {
  border-color: var(--color-level-warn);
}

.tl li.CLOSED i {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal);
}

.tl li.WAIT {
  color: var(--color-text-weak);
}

.tl li.WAIT i {
  border-style: dashed;
}

.tl .at {
  margin-right: 6px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.tl b {
  font-weight: 600;
}

.tl .by {
  color: var(--color-text-sub);
}

/* 체크리스트 · 셧다운 단계 */
.ck {
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.ck li {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  padding: 9px 12px;
  border-top: 1px solid #f1f5f9;
  font-size: 13px;
  color: var(--color-text-sub);
}

.ck li:first-child {
  border-top: none;
}

.ck li.done {
  color: var(--color-text);
}

.ck .box {
  width: 18px;
  height: 18px;
  margin-top: 1px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--color-border-strong);
  border-radius: 4px;
}

.ck .done .box {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal);
  color: #fff;
}

.ck .memo {
  display: block;
  margin-top: 4px;
  padding: 6px 8px;
  border-radius: 4px;
  background: var(--color-surface-sub);
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.ck .res {
  font-size: 12px;
  color: var(--color-text-sub);
}

.ck .t {
  font-size: 12px;
  color: var(--color-text-weak);
}

.foot {
  flex: none;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 20px;
  border-top: 1px solid var(--color-border);
}

.btn {
  height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--m-hover);
}

.btn:hover:not(:disabled) {
  background: var(--color-surface-sub);
}

.btn.primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.btn:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
