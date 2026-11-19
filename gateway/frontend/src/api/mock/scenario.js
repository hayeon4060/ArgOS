/**
 * 예시 상황 (정상 / 주의 / 경보 / 화재) — 예시 데이터 모드의 '한 곳'
 *
 * - 백엔드 없이 알림 대응 흐름을 보려고, 상도 건조로의 지금 상황을 여기 하나에 둠
 *   모든 예시 데이터(사업장·공장·공정·설비·알림·상단 바)가 이 값으로 숫자와 색을 만듦 → 화면끼리 항상 같은 값
 * - 상황이 바뀌면 emitLive() → 열려 있는 화면이 조용히 다시 불러옴 (api/live.js)
 *   실제 서버에서는 이 파일 대신 SSE 이벤트가 같은 일을 함
 * - 상황 바꾸기 도구(features/demo/DemoTool.vue, 개발 모드에서만)와 주의 확인·체크리스트 API 흉내가 아래 함수를 부름
 * - 새로고침해도 이어지게 sessionStorage 에 저장 (브라우저 탭을 닫으면 처음 상황으로)
 * - 개발 서버에서는 웹·담당자 앱(휴대폰)이 같은 상황을 봄: 바뀌면 개발 서버(mockSync.js)에 올리고,
 *   다른 화면이 바꾼 상황을 받아 덮어씀 (scenarioSynced = 처음 한 번 맞춘 뒤)
 *
 * 처음 상황(기본값) = 그동안 화면들이 보여 주던 상황:
 *   16:38:20 상도 건조로 배기팬 전류 CT-17 22.4 A 주의 → 16:38:31 확인 → 16:41:05 체크리스트 전송 → 김도현 2/5 진행 중
 *
 * 시각: 위의 시각표(16:38:20 · 16:52:08 · 16:58:04 …)는 화면설계안의 시각.
 *   상황을 바꾸는 순간을 '지금'으로 삼아 시각표 전체를 같은 간격만큼 옮김 (anchorAt)
 *   → 이 상황(상도 건조로 주의 · 경보 · 화재)의 시각은 오후 4시 전에 시연해도 상단 시계보다 미래로 보이지 않음. 시각 사이 간격은 설계안 그대로
 *   (그 밖의 오늘 기록 — 오전·오후의 해제된 알림, 점검 일정 17:00 등 — 은 고정 시각 그대로라 이른 시각에는 미래로 보일 수 있음)
 *   옮긴 간격은 scenario.shiftSec 에 두고, 다른 예시 데이터의 설계안 시각도 designAt() 으로 같이 옮김
 *   (자정 바로 뒤에 상황을 바꾸면 앞선 시각이 전날 밤 시각이 될 수 있음 — 예시 데이터라 날짜는 따로 맞추지 않음)
 */
import { reactive, toRaw, watch } from 'vue'
import { emitLive } from '../live'
import { LEVEL } from '@/types/codes'
import { USE_MOCK } from './index'

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

/* ───────── 설계안 시각표를 '지금' 기준으로 옮기기 ───────── */

const DAY_SEC = 24 * 3600
const TIME_RE = /^\d{2}:\d{2}:\d{2}$/
/** 하루 안으로 (음수·24시 넘음 → 0~24시) */
const wrapDay = (sec) => ((sec % DAY_SEC) + DAY_SEC) % DAY_SEC
/** 지금 PC 시각 (0시부터 초) */
const nowSec = () => {
  const d = new Date()
  return d.getHours() * 3600 + d.getMinutes() * 60 + d.getSeconds()
}

/** 'HH:mm:ss' 를 sec 초만큼 옮김 (비어 있으면 그대로) */
export const shiftTime = (t, sec) => (t ? toTime(wrapDay(toSec(t) + sec)) : t)

/** 값 안의 'HH:mm:ss' 문자열을 모두 sec 초만큼 옮김 (객체 · 배열 안까지) */
function shiftDeep(v, sec) {
  if (typeof v === 'string') return TIME_RE.test(v) ? shiftTime(v, sec) : v
  if (Array.isArray(v)) return v.map((x) => shiftDeep(x, sec))
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, shiftDeep(x, sec)]))
  return v
}

/**
 * 설계안 시각표로 만든 상황을, base 시각이 '지금'이 되도록 옮김
 * @param {string} base 설계안에서 이 상황이 시작된 시각 (예: 주의 발생 '16:38:20')
 * @param {object} next 설계안 시각으로 적은 상황 값
 * @returns {object} 시각을 옮긴 상황 값 + shiftSec(옮긴 초)
 */
export function anchorAt(base, next) {
  const shiftSec = nowSec() - toSec(base)
  return { ...shiftDeep(next, shiftSec), shiftSec }
}

/** 처음 상황 */
function initial() {
  return anchorAt('16:42:10', {
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
  })
}

/** 경보·화재가 없는 상태 (상황을 통째로 바꿀 때 같이 비움) */
export const NO_EMERGENCY = () => ({ incident: null, fire: null, shutdown: null, shut: false })

function load() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY))
    // 예전에 저장된 값에 새 칸이 없을 수 있어 처음 값 위에 덮음
    // shiftSec 이 없는 예전 값은 설계안 시각 그대로였으므로 0
    if (saved?.state) return { ...initial(), shiftSec: 0, ...saved }
  } catch {
    // 저장된 값을 못 읽으면 처음 상황
  }
  return initial()
}

/** 지금 예시 상황 (화면에서 직접 고치지 말고 아래 함수로만 바꿈) */
export const scenario = reactive(load())

/* ───────── 화면끼리 같은 상황 (개발 서버 중계) ───────── */

// 설치형 앱(APK) 시연 빌드는 앱 안에 개발 서버가 없으므로 PC 개발 서버 주소를 빌드할 때 넣음 (담당자 앱 .env.production.local)
// 앱 로그인 화면에서 바꾼 PC 주소(argos.relay)가 있으면 그것 — PC 주소가 바뀌어도 다시 빌드하지 않게
const RELAY = import.meta.env.DEV ? '' : savedRelay() || (import.meta.env.VITE_MOCK_RELAY ?? '')
function savedRelay() {
  try {
    return localStorage.getItem('argos.relay') ?? ''
  } catch {
    return ''
  }
}
const SYNC = USE_MOCK && (import.meta.env.DEV || !!RELAY)
const SYNC_URL = `${RELAY}/__mock/scenario`
const syncId = Math.random().toString(36).slice(2) // 내가 올린 것은 다시 받지 않음
let synced = '' // 마지막으로 서버와 같았던 상황 (JSON) — 같으면 다시 올리지 않음

function pushScenario(json) {
  synced = json
  fetch(SYNC_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: `{"from":"${syncId}","state":${json}}` }).catch(
    () => {}, // 개발 서버가 꺼져 있으면 이 화면 혼자 동작
  )
}

function applyRemote(state) {
  for (const k of Object.keys(scenario)) if (!(k in state)) delete scenario[k]
  Object.assign(scenario, state)
  synced = JSON.stringify(toRaw(scenario))
  emitLive('level.changed')
}

/** 처음 한 번 서버 상황에 맞춤 (서버에 아직 없으면 내 상황을 올림). 개발 서버가 아니면 바로 끝 */
export const scenarioSynced = !SYNC
  ? Promise.resolve()
  : new Promise((resolve) => {
      const sync = () =>
        fetch(SYNC_URL)
          .then((r) => r.json())
          .then(({ state }) => (state ? applyRemote(state) : pushScenario(JSON.stringify(toRaw(scenario)))))
          .catch(() => {})
          .finally(resolve)
      const es = new EventSource(`${SYNC_URL}/stream`)
      es.onopen = sync // 개발 서버를 다시 켜서 다시 연결돼도 맞춤
      es.onerror = resolve // 중계가 없는 서버면 기다리지 않음
      es.onmessage = (e) => {
        const { from, state } = JSON.parse(e.data)
        if (from !== syncId) applyRemote(state)
      }
    })

// 바뀔 때마다 저장 (+ 개발 서버에 올림)
watch(
  scenario,
  () => {
    const json = JSON.stringify(toRaw(scenario))
    try {
      sessionStorage.setItem(STORAGE_KEY, json)
    } catch {
      // 저장 못 해도 화면은 그대로 동작
    }
    if (SYNC && json !== synced) pushScenario(json)
  },
  { deep: true },
)

/** 예시 시계의 지금 시각 'HH:mm:ss' */
export function scenarioNow() {
  const passed = Math.floor((Date.now() - scenario.clock.at) / 1000)
  return toTime(toSec(scenario.clock.base) + passed)
}

/** 설계안 시각(예: 경보 전의 '16:47:36')을 지금 상황의 시각으로 옮김 — 예시 데이터 파일들이 씀 */
export const designAt = (t) => shiftTime(t, scenario.shiftSec ?? 0)

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

/** 정상 (알림 없음, 설계안 16:30:05 → 지금) */
export function setNormal() {
  replace(
    anchorAt('16:30:05', { state: LEVEL.NORMAL, alarm: null, checklist: null, ...NO_EMERGENCY(), clock: { base: '16:30:05', at: Date.now() } }),
    'level.changed',
  )
}

/** 주의 발생 (설계안 16:38:20 → 지금, CT-17 22.4 A, 아직 아무도 확인 안 함) */
export function raiseWarn() {
  replace(
    anchorAt('16:38:20', {
      state: LEVEL.WARN,
      alarm: { id: 1042, status: 'OPEN', raisedAt: '16:38:20', ackedAt: null, ackedBy: null, closedAt: null },
      checklist: null,
      ...NO_EMERGENCY(),
      clock: { base: '16:38:20', at: Date.now() },
    }),
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
    reportedAt: null,
    confirmedAt: null,
    result: null,
  }
  emitLive('checklist.updated')
  // 담당자가 앱을 켜 두었으면 사람이 앱에서 체크 (phoneUsers: 담당자 앱 api/myChecklists.js markPhoneUser)
  if (!scenario.phoneUsers?.includes(assigneeId)) startAutoTick()
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
