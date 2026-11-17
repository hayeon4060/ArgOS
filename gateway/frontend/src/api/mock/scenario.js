/**
 * 예시 상황 (정상 / 주의 / 경보 / 화재) — 예시 데이터 모드의 '한 곳'
 *
 * - 백엔드 없이 알림 대응 흐름을 보려고, 상도 건조로의 지금 상황을 여기 하나에 둠
 *   모든 예시 데이터(사업장·공장·공정·설비·알림·상단 바)가 이 값으로 숫자와 색을 만듦 → 화면끼리 항상 같은 값
 * - 상황이 바뀌면 emitLive() → 열려 있는 화면이 조용히 다시 불러옴 (api/live.js)
 *   실제 서버에서는 이 파일 대신 SSE 이벤트가 같은 일을 함
 * - 상황 바꾸기 도구(features/demo/DemoTool.vue, 개발 모드에서만)와 주의 확인·체크리스트 API 흉내가 아래 함수를 부름
 * - 여러 화면이 같은 상황을 봄 (실제 서버에서는 SSE 가 하는 일)
 *   · 개발 서버(npm run dev): 개발 서버가 상황 하나를 들고 중계 (vite.config.js mockSync)
 *     → PC 관제 웹과 휴대폰 담당자 앱(/phone/)처럼 다른 기기끼리도 바로 같이 바뀜
 *   · 개발 서버가 없으면: 같은 브라우저 창끼리만 (localStorage storage 이벤트)
 *   localStorage 에도 저장해 새로고침해도 이어짐. 처음 상황으로 돌리려면 상황 바꾸기 도구 '처음 상황'
 * - phoneUsers: 담당자 앱을 켜 둔 공정 담당자 id 목록 — 받는 사람이 앱에 있으면 체크리스트 자동 진행을 안 함
 * - 담당자 앱이 남긴 항목별 결과·메모는 checklist.results[i] = { result, memo }, 완료 보고 한 줄은 checklist.reportMemo
 *   (phone/src/api/myChecklists.js 가 씀. 없으면 예전처럼 '이상 없음' · 2번째 항목 현장 메모)
 *
 * 처음 상황(기본값) = 그동안 화면들이 보여 주던 상황:
 *   16:38:20 상도 건조로 배기팬 전류 CT-17 22.4 A 주의 → 16:38:31 확인 → 16:41:05 체크리스트 전송 → 김도현 2/5 진행 중
 */
import { reactive, toRaw, watch } from 'vue'
import { emitLive } from '../live'
import { LEVEL } from '@/types/codes'

const STORAGE_KEY = 'argos.mock.scenario'

/** 상도 건조로 센서 값 (화면설계안 js/lib.js VAL) — 센서 id: 117 전류 CT-17, 215 온도 TE-15, 313 CO CO-13 */
export const TOP_OVEN_VALUES = {
  [LEVEL.NORMAL]: { 117: 18.1, 215: 142, 313: 6 },
  [LEVEL.WARN]: { 117: 22.4, 215: 146, 313: 9 },
  [LEVEL.ALARM]: { 117: 27.5, 215: 164, 313: 34 },
  [LEVEL.FIRE]: { 117: 0.0, 215: 212, 313: 120 },
}

/** 셧다운으로 상도 건조로를 멈춘 뒤 값 (설계안 curVal() S.shut: 전류 0, 온도 96 ℃, CO 8 ppm) */
export const SHUT_VALUES = { 117: 0.0, 215: 96, 313: 8 }

/** 조치 체크리스트 기본 5개 항목 (설계안 js/core.js CKLIST, 명세서 GET /checklist-templates 예시) */
export const DEFAULT_ITEMS = [
  '배기팬 이상 소음·진동 확인',
  '구동 벨트 장력, 베어링 발열 확인',
  '배기 덕트 댐퍼 열림 상태 확인',
  '버너실 CO 농도 휴대용 측정기로 재측정',
  '건조로 온도 지시값과 설정값 비교',
]

/** 도장 공정 담당자 (공정 배치도 예시 데이터의 담당자 이름과 같음) */
export const WORKERS = [
  { id: 21, name: '김도현', loginId: 'u5_paint01', onDuty: true, pushReady: true },
  { id: 22, name: '박성훈', loginId: 'u5_paint02', onDuty: true, pushReady: true },
  { id: 23, name: '이재호', loginId: 'u5_paint03', onDuty: false, pushReady: true },
]

/** 현장 메모 (설계안: 2번째 항목을 마칠 때 남김) */
const FIELD_MEMO = '구동 벨트 장력 느슨함 확인, 재조정함. 베어링 발열은 없음.'

const pad = (n) => String(n).padStart(2, '0')
const toSec = (t) => {
  const [h, m, s] = t.split(':').map(Number)
  return h * 3600 + m * 60 + s
}
const toTime = (sec) => `${pad(Math.floor(sec / 3600) % 24)}:${pad(Math.floor(sec / 60) % 60)}:${pad(sec % 60)}`

/** 처음 상황 */
function initial() {
  return {
    /** 'NORMAL' | 'WARN' | 'ALARM' | 'FIRE' */
    state: LEVEL.WARN,
    /** 상도 건조로 전류 주의 알림 (없으면 null). status: OPEN → ACKED → ACTING → CLOSED */
    alarm: { id: 1042, status: 'ACTING', raisedAt: '16:38:20', ackedAt: '16:38:31', ackedBy: '한지우', closedAt: null },
    /** 체크리스트 (없으면 null). status: SENT → IN_PROGRESS → REPORTED(앱에서 완료 보고) → CONFIRMED */
    checklist: {
      id: 77,
      status: 'IN_PROGRESS',
      items: [...DEFAULT_ITEMS],
      assigneeId: 21,
      sentAt: '16:41:05',
      sentBy: '한지우',
      doneTimes: ['16:41:30', '16:41:48'],
      memo: { at: '16:41:48', text: FIELD_MEMO },
      remindCount: 0,
      lastRemindMs: 0, // 다시 알림 30초 제한 계산용 (실제 시계)
      reassigned: [], // [{ at, from, to, reason }]
      reportedAt: null,
      confirmedAt: null,
      result: null,
    },
    /** 경보 (3단계) 한 건: { id, status: OPEN | CLOSED, raisedAt, closedAt, result } — 없으면 null (scenarioEmergency.js) */
    incident: null,
    /** 화재: { id, status: ACTIVE | CLEARED, detectedAt, failCount, locked, clearedAt, clearedBy } — 없으면 null */
    fire: null,
    /** 셧다운 진행: { equipmentIds, startedAt, steps: [시각|null ×3] } — 없으면 null */
    shutdown: null,
    /** 상도 건조로가 멈춘 상태 (셧다운·화재 뒤, 현장 점검 전) */
    shut: false,
    /** 기준값 관리에서 저장한 기준 { 센서 id: { 계절: { warn, alarm } } } (thresholdRules.js) */
    thresholds: {},
    /** 기준값 관리에서 저장한 변경 이력 (thresholdData.js 가 기본 이력 위에 붙임) */
    thresholdHistory: [],
    /** 설비별 기준값 판 번호 (동시에 고치면 409) */
    thresholdVersion: {},
    /** 예시 시계: base 시각에서 실제로 흐른 초를 더함 */
    clock: { base: '16:42:10', at: Date.now() },
  }
}

/** 경보·화재가 없는 상태 (상황을 통째로 바꿀 때 같이 비움) */
export const NO_EMERGENCY = () => ({ incident: null, fire: null, shutdown: null, shut: false })

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY))
    // 예전에 저장된 값에 새 칸이 없을 수 있어 처음 값 위에 덮음
    if (saved?.state) return { ...initial(), ...saved }
  } catch {
    // 저장된 값을 못 읽으면 처음 상황
  }
  return initial()
}

/** 지금 예시 상황 (화면에서 직접 고치지 말고 아래 함수로만 바꿈) */
export const scenario = reactive(load())

/** 마지막으로 저장했거나 받은 값 (같은 값을 다시 보내 화면끼리 주고받기가 끝없이 이어지지 않게) */
let lastJson = null

/** 개발 서버 중계 주소 (vite.config.js mockSync). 개발 서버가 없으면(배포 빌드·Capacitor 앱) 이 기기 안에서만 */
const SYNC_URL = import.meta.env.DEV ? '/__mock/scenario' : null
/** 처음에 중계 서버 값을 받기 전에는 보내지 않음 (휴대폰의 오래된 상황이 PC 의 지금 상황을 덮지 않게) */
let synced = !SYNC_URL

function send(json) {
  fetch(SYNC_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: json }).catch(() => {
    // 개발 서버가 꺼졌으면 이 기기 안에서만 계속
  })
}

// 바뀔 때마다 저장 (+ 중계 서버로)
watch(
  scenario,
  () => {
    const json = JSON.stringify(toRaw(scenario))
    if (json === lastJson) return
    lastJson = json
    try {
      localStorage.setItem(STORAGE_KEY, json)
    } catch {
      // 저장 못 해도 화면은 그대로 동작
    }
    if (SYNC_URL && synced) send(json)
  },
  { deep: true },
)

/** 다른 화면이 바꾼 상황을 받아 열린 화면을 다시 불러옴 */
function applyRemote(json) {
  if (!json || json === lastJson) return // 내가 보낸 값이 되돌아온 것
  try {
    const next = JSON.parse(json)
    if (!next?.state) return
    stopTick() // 다른 화면이 상황을 움직이고 있으면 이 화면의 자동 진행은 멈춤
    Object.assign(scenario, next)
    // 받은 글자 그대로가 아니라 이 화면 기준으로 다시 만든 값: 화면마다 키 순서가 달라도 되돌려 보내지 않게
    lastJson = JSON.stringify(toRaw(scenario))
    emitLive('scenario.synced')
  } catch {
    // 깨진 값은 무시
  }
}

/** 중계 서버와 처음 맞추기가 끝나면 풀림 (담당자 앱이 '앱 켜 둠' 표시를 이 뒤에 함) */
export const scenarioSynced = SYNC_URL
  ? fetch(SYNC_URL)
      .then((r) => r.text())
      .then((json) => {
        synced = true
        if (json && json !== 'null') applyRemote(json)
        else send((lastJson = JSON.stringify(toRaw(scenario)))) // 중계 서버가 비었으면 이 화면 값으로 시작
      })
      .catch(() => {
        synced = true
      })
  : Promise.resolve()

if (SYNC_URL) {
  // 다른 기기·창이 바꾸면 중계 서버가 바로 보내 줌 (끊기면 브라우저가 알아서 다시 연결)
  new EventSource(`${SYNC_URL}/stream`).onmessage = (e) => applyRemote(e.data)
} else {
  // 개발 서버가 없으면 같은 브라우저의 다른 창끼리만 (localStorage)
  window.addEventListener('storage', (e) => e.key === STORAGE_KEY && applyRemote(e.newValue))
}

/** 받는 담당자가 담당자 앱을 켜 두었는지 (그러면 사람이 직접 체크) — 앱이 phoneUsers 에 표시 (phone/src/api/myChecklists.js) */
const inPhoneApp = (userId) => scenario.phoneUsers?.includes(userId) ?? false

/** 예시 시계의 지금 시각 'HH:mm:ss' */
export function scenarioNow() {
  const passed = Math.floor((Date.now() - scenario.clock.at) / 1000)
  return toTime(toSec(scenario.clock.base) + passed)
}

/** 'HH:mm:ss' 두 시각 사이 초 */
export const secondsBetween = (from, to) => toSec(to) - toSec(from)

/**
 * 상도 건조로 센서 지금 값 (117·215·313 이 아니면 undefined)
 * - 정상: 셧다운으로 멈췄으면 0 A · 96 ℃ · 8 ppm, 아니면 평소 값
 * - 경보 중 셧다운: 단계마다 값이 내려감 (설계안 shutdownSeq: 연료 차단 → CO 26, 컨베이어 정지 → 온도 151, 배기팬 정지 → 전류 0)
 */
export function topOvenValue(sensorId) {
  if (!(sensorId in SHUT_VALUES)) return undefined
  if (scenario.state === LEVEL.NORMAL && scenario.shut) return SHUT_VALUES[sensorId]
  const v = { ...TOP_OVEN_VALUES[scenario.state] }
  if (scenario.state === LEVEL.ALARM && scenario.shutdown) {
    const done = scenario.shutdown.steps.filter(Boolean).length
    if (done >= 1) v[313] = 26
    if (done >= 2) v[215] = 151
    if (done >= 3) v[117] = 0.0
  }
  return v[sensorId]
}

/** 진행 중인 체크리스트 (확인 끝났으면 null) */
export function activeChecklist() {
  const c = scenario.checklist
  return c && c.status !== 'CONFIRMED' ? c : null
}

/* ───────── 상황 바꾸기 (상황 바꾸기 도구) ───────── */

let tickTimer = null
function stopTick() {
  clearInterval(tickTimer)
  tickTimer = null
}

/** 상황을 통째로 바꾸고 화면에 알림 (경보·화재 상황도 이 함수로) */
export function replace(next, event) {
  stopTick()
  Object.assign(scenario, next)
  emitLive(event)
}

/** 정상 (알림 없음, 오늘 16:30:05) */
export function setNormal() {
  replace(
    { state: LEVEL.NORMAL, alarm: null, checklist: null, ...NO_EMERGENCY(), clock: { base: '16:30:05', at: Date.now() } },
    'level.changed',
  )
}

/** 주의 발생 (16:38:20 CT-17 22.4 A, 아직 아무도 확인 안 함) */
export function raiseWarn() {
  replace(
    {
      state: LEVEL.WARN,
      alarm: { id: 1042, status: 'OPEN', raisedAt: '16:38:20', ackedAt: null, ackedBy: null, closedAt: null },
      checklist: null,
      ...NO_EMERGENCY(),
      clock: { base: '16:38:20', at: Date.now() },
    },
    'alarm.created',
  )
}

/** 처음 상황으로 (주의 · 조치 중 2/5) */
export function resetScenario() {
  replace(initial(), 'level.changed')
}

/* ───────── 주의 대응 (API 흉내가 부름) ───────── */

/** 주의 확인 (2초 길게 누르기) */
export function ackAlarm(by) {
  if (!scenario.alarm || scenario.alarm.status !== 'OPEN') return false
  scenario.alarm.status = 'ACKED'
  scenario.alarm.ackedAt = scenarioNow()
  scenario.alarm.ackedBy = by
  emitLive('alarm.updated')
  return true
}

/** 체크리스트 전송 → 알림 '조치 중', 담당자 앱이 항목을 하나씩 체크하는 흉내 시작 */
export function sendChecklist({ items, assigneeId, sentBy }) {
  scenario.alarm.status = 'ACTING'
  scenario.checklist = {
    id: (scenario.checklist?.id ?? 76) + 1,
    status: 'SENT',
    items: [...items],
    assigneeId,
    sentAt: scenarioNow(),
    sentBy,
    doneTimes: [],
    memo: null,
    remindCount: 0,
    lastRemindMs: 0,
    reassigned: [],
    results: [], // 담당자 앱 항목별 { result, memo }
    reportedAt: null,
    reportMemo: null,
    confirmedAt: null,
    result: null,
  }
  emitLive('checklist.updated')
  if (!inPhoneApp(assigneeId)) startAutoTick()
  return scenario.checklist
}

/**
 * 담당자 앱에서 항목 n개 완료 흉내 (상황 바꾸기 도구 '+1 항목' / '모두 완료', 자동 진행)
 * - 모두 끝나면 앱에서 '완료 보고'까지 한 것으로 봄 (status REPORTED) → 공장 담당자가 조치 확인 가능
 */
export function tickChecklist(count = 1) {
  const c = activeChecklist()
  if (!c || c.status === 'REPORTED') return
  for (let i = 0; i < count && c.doneTimes.length < c.items.length; i++) {
    c.doneTimes.push(scenarioNow())
    if (c.doneTimes.length === 2 && !c.memo) c.memo = { at: scenarioNow(), text: FIELD_MEMO }
  }
  c.status = c.doneTimes.length >= c.items.length ? 'REPORTED' : 'IN_PROGRESS'
  if (c.status === 'REPORTED') {
    c.reportedAt = scenarioNow()
    stopTick()
  }
  emitLive('checklist.updated')
}

/** 전송한 뒤에만 5.2초마다 한 항목씩 자동 진행 (설계안 sendChecklist 와 같은 간격). 새로고침하면 멈춤 */
export function startAutoTick() {
  stopTick()
  tickTimer = setInterval(() => tickChecklist(1), 5200)
}

/** 다시 알림 (30초에 한 번 — 백엔드 ChecklistService REMIND_COOLDOWN) */
export const REMIND_COOLDOWN_MS = 30_000
export function remindChecklist() {
  const c = activeChecklist()
  const wait = c.lastRemindMs + REMIND_COOLDOWN_MS - Date.now()
  if (wait > 0) return { ok: false, availableAt: new Date(Date.now() + wait).toISOString() }
  c.remindCount += 1
  c.lastRemindMs = Date.now()
  emitLive('checklist.updated')
  return { ok: true, remindCount: c.remindCount, availableAt: new Date(Date.now() + REMIND_COOLDOWN_MS).toISOString() }
}

/** 담당자 변경 (이미 한 항목은 그대로) */
export function reassignChecklist(assigneeId, reason) {
  const c = activeChecklist()
  c.reassigned.push({ at: scenarioNow(), from: c.assigneeId, to: assigneeId, reason })
  c.assigneeId = assigneeId
  emitLive('checklist.updated')
}

/** 조치 확인 → 체크리스트 확인 끝, 알림 해제, 상도 건조로 값 정상으로 */
export function confirmChecklist({ result, memo }) {
  const c = activeChecklist()
  const now = scenarioNow()
  c.status = 'CONFIRMED'
  c.confirmedAt = now
  c.result = { result, memo }
  scenario.alarm.status = 'CLOSED'
  scenario.alarm.closedAt = now
  scenario.state = LEVEL.NORMAL
  stopTick()
  emitLive('alarm.updated')
}

/** 담당자 이름 */
export const workerName = (id) => WORKERS.find((w) => w.id === id)?.name ?? ''
