/**
 * 알림 대응(주의) 예시 응답 — 모두 예시 상황(scenario.js)에서 만듦
 * - 응답 모양: API 명세서 C1·C2 와 백엔드 gateway/backend 의 AlarmService·ChecklistService 응답 필드
 *   GET /alarms/{id}          : 조치 내역(timeline)
 *   GET /alarms/{id}/detail   : 주의 상세 (원인, 그때 센서값, 열화상 1장, 추이, 원인 후보)
 *   GET /checklists/{id}      : 체크리스트 한 건
 */
import { alarmTrendPoints, alarmTrendPointsOf } from './equipmentSeries'
import { mockAlarms, todayAt } from './data'
import { THERMAL_BY_STATE, mockEquipmentEvents } from './equipmentData'
import { EQUIPMENT, equipmentLevel, runStateOf, sensorsNow } from './processData'
import { fanAutoOf } from './fanAuto'
import { GAS_WARN_VALUES, TOP_OVEN_VALUES, designAt, scenario, scenarioNow, workerName } from './scenario'
import { LEVEL } from '@/types/codes'
import irWarn from '@/assets/images/thermal/ir_warn.jpg'

const pad = (n) => String(n).padStart(2, '0')
/** 'HH:mm:ss' + 초 */
function addSec(time, sec) {
  const [h, m, s] = time.split(':').map(Number)
  const t = h * 3600 + m * 60 + s + sec
  return `${pad(Math.floor(t / 3600) % 24)}:${pad(Math.floor(t / 60) % 60)}:${pad(t % 60)}`
}

/* ───────── GET /alarms/{id} : 조치 내역 ─────────
 * timeline[]: { step, at, by, text } — step 은 백엔드와 같은 이름
 *   RAISED 발생 · ACK 확인 · CHECKLIST 체크리스트 전송 · REASSIGN 담당자 변경 · REPORT 완료 보고 · CONFIRM 조치 확인
 */
const step = (name, time, by = null, text = '') => ({ step: name, at: todayAt(time), by, text })

/** 상도 건조로 주의 (예시 상황 그대로) */
function topOvenTimeline() {
  const a = scenario.alarm
  const c = scenario.checklist
  const list = [step('RAISED', a.raisedAt, null, '발생')]
  if (a.ackedAt) list.push(step('ACK', a.ackedAt, a.ackedBy, '알림 확인'))
  if (c) {
    list.push(step('CHECKLIST', c.sentAt, c.sentBy, `체크리스트 전송 → ${workerName(c.reassigned[0]?.from ?? c.assigneeId)}`))
    c.reassigned.forEach((r) => list.push(step('REASSIGN', r.at, c.sentBy, `담당자 변경 → ${workerName(r.to)}`)))
    if (c.reportedAt) list.push(step('REPORT', c.reportedAt, workerName(c.assigneeId), '체크리스트 완료 보고'))
    if (c.confirmedAt) list.push(step('CONFIRM', c.confirmedAt, c.sentBy, '조치 확인'))
  }
  return list
}

/** 이미 끝난 알림: 설계안 actSteps() 처럼 발생 → 확인 → 전송 → 완료 → 해제 */
function pastTimeline(raisedTime) {
  return [
    step('RAISED', raisedTime, null, '발생'),
    step('ACK', addSec(raisedTime, 14), '한지우', '알림 확인'),
    step('CHECKLIST', addSec(raisedTime, 150), '한지우', '체크리스트 전송 → 박성훈'),
    step('REPORT', addSec(raisedTime, 560), '박성훈', '체크리스트 완료 보고'),
    step('CONFIRM', addSec(raisedTime, 640), '한지우', '조치 확인'),
  ]
}

/**
 * 경보·화재 알림의 조치 내역 (설계안 actSteps() 'dng' · 'flame')
 * - 경보: 발생 → 공정 담당자 알림(PUSH) → 설비 셧다운(SHUTDOWN) → 경보 종료(CLOSE)
 * - 화재: 감지 → 경고 방송(BROADCAST) → 자동 셧다운(SHUTDOWN) → 본사 통보(HQ_NOTIFY) → 경보 해제(CLEAR)
 */
function emergencyTimeline(row) {
  const t = row.raisedAt.slice(11, 19)
  if (row.level === LEVEL.FIRE) {
    const f = scenario.fire
    return [
      step('RAISED', t, null, '화재 감지'),
      step('BROADCAST', addSec(t, 1), null, '경고 방송'),
      step('SHUTDOWN', addSec(t, 2), null, '자동 셧다운'),
      step('HQ_NOTIFY', addSec(t, 2), null, '본사 통보'),
      ...(f?.clearedAt ? [step('CLEAR', f.clearedAt, f.clearedBy, '경보 해제')] : []),
    ]
  }
  const sd = scenario.shutdown
  const inc = scenario.incident
  return [
    step('RAISED', t, null, '발생'),
    step('PUSH', addSec(t, 1), null, '공정 담당자 알림'),
    ...(sd?.steps[2] ? [step('SHUTDOWN', sd.steps[2], null, '설비 셧다운')] : []),
    ...(inc?.closedAt ? [step('CLOSE', inc.closedAt, null, '경보 종료')] : []),
  ]
}

export function alarmTimeline(alarmId) {
  const row = mockAlarms().find((a) => a.id === Number(alarmId))
  if (!row) return null
  return {
    id: row.id,
    level: row.level,
    status: row.status,
    title: row.title,
    location: row.location,
    sensorTag: row.sensorTag,
    value: row.value,
    unit: row.unit,
    threshold: row.threshold,
    timeline:
      row.id === 1042
        ? topOvenTimeline()
        : row.id >= 1049
          ? emergencyTimeline(row)
          : pastTimeline(row.raisedAt.slice(11, 19)),
    checklist: row.checklist,
  }
}

/* ───────── GET /alarms/{id}/detail : 주의 상세 (상도 건조로 1042 만) ───────── */
export function alarmDetail(alarmId) {
  if (Number(alarmId) === scenario.extra?.id) return extraAlarmDetail(scenario.extra)
  const a = scenario.alarm
  if (Number(alarmId) !== 1042 || !a) return null
  if (a.kind === 'GAS') return gasAlarmDetail(a)
  const v = TOP_OVEN_VALUES[LEVEL.WARN] // 주의가 걸린 그 순간 값 (지금 값이 아님)
  return {
    id: 1042,
    level: LEVEL.WARN,
    status: a.status,
    location: '울산공장 · 5공장 · 도장 공정 · 상도 건조로 배기팬',
    raisedAt: todayAt(a.raisedAt),
    cause: '배기팬 전류가 주의 기준(21.6 A)을 3분 이상 넘었습니다.',
    rule: { sensorTag: 'CT-17', warn: 21.6, alarm: 27.0, holdSec: 180, max: 36 },
    usual: 18.0, // 명세서에 없음 ('평소 18.0 A 대비 124%')
    sensors: [
      { tag: 'CT-17', type: 'CURRENT', value: v[117], unit: 'A', warn: 21.6, alarm: 27, level: LEVEL.WARN },
      { tag: 'TE-15', type: 'TEMP', value: v[215], unit: '℃', warn: 160, alarm: 180, level: LEVEL.NORMAL },
      { tag: 'CO-13', type: 'CO', value: v[313], unit: 'ppm', warn: 20, alarm: 30, level: LEVEL.NORMAL },
    ],
    thermal: {
      imageUrl: irWarn,
      capturedAt: todayAt(a.raisedAt),
      max: 78.4,
      min: 22.8,
      avg: 41.3,
      usualAtMax: 52.0, // 평소 같은 위치 온도 (명세서에 없음)
      maxPoint: { x: 0.62, y: 0.13 },
      zoneName: '배기팬 모터부',
      cameraTag: 'IR-01',
    },
    trend: { sensorId: 117, tag: 'CT-17', unit: 'A', warn: 21.6, alarm: 27, points: alarmTrendPoints() },
    // 센서별 추이 (2026-11-19 추가, 백엔드 요청 docs/api/warn-detail-trends.md) — 화면은 화재면 온도·전류를 고름
    trends: [
      { sensorId: 117, tag: 'CT-17', type: 'CURRENT', unit: 'A', warn: 21.6, alarm: 27, points: alarmTrendPoints() },
      { sensorId: 215, tag: 'TE-15', type: 'TEMP', unit: '℃', warn: 160, alarm: 180, points: alarmTrendPointsOf('TEMP', v[215]) },
      { sensorId: 313, tag: 'CO-13', type: 'CO', unit: 'ppm', warn: 20, alarm: 30, points: alarmTrendPointsOf('CO', v[313]) },
    ],
    causeCandidates: ['베어링 마모', '구동 벨트 장력 이상', '배기 덕트 댐퍼 막힘'],
    equipmentType: 'OVEN',
    sensorType: 'CURRENT',
    hazard: 'FIRE', // 전류·온도 → 화재 (백엔드 alarm.hazard)
    processId: 12,
    equipmentId: 31,
  }
}

/**
 * 가스 주의 상세 (상도 건조로 버너실 CO-13 24 ppm, 2026-11-19 추가)
 * - 모양은 전류 주의와 같고, 배기팬 자동 제어 결과(fanAuto)를 같이 줌
 */
function gasAlarmDetail(a) {
  const v = GAS_WARN_VALUES
  const raisedAt = todayAt(a.raisedAt)
  return {
    id: 1042,
    level: LEVEL.WARN,
    status: a.status,
    location: '울산공장 · 5공장 · 도장 공정 · 상도 건조로 버너실',
    raisedAt,
    cause: '버너실 CO 농도가 주의 기준(20 ppm)을 넘었습니다.',
    rule: { sensorTag: 'CO-13', warn: 20, alarm: 30, holdSec: 10, max: 40 },
    usual: 6,
    sensors: [
      { tag: 'CT-17', type: 'CURRENT', value: v[117], unit: 'A', warn: 21.6, alarm: 27, level: LEVEL.NORMAL },
      { tag: 'TE-15', type: 'TEMP', value: v[215], unit: '℃', warn: 160, alarm: 180, level: LEVEL.NORMAL },
      { tag: 'CO-13', type: 'CO', value: v[313], unit: 'ppm', warn: 20, alarm: 30, level: LEVEL.WARN },
    ],
    thermal: {
      imageUrl: irWarn,
      capturedAt: raisedAt,
      max: 78.4,
      min: 22.8,
      avg: 41.3,
      usualAtMax: 52.0,
      maxPoint: { x: 0.62, y: 0.13 },
      zoneName: '버너실 앞',
      cameraTag: 'IR-01',
    },
    // 추이: 16:00 부터 6 ppm 근처 → 마지막에 24 ppm 으로 오름 (예시)
    trend: { sensorId: 313, tag: 'CO-13', unit: 'ppm', warn: 20, alarm: 30, points: gasTrendPoints() },
    // 센서별 추이 — 화면은 가스면 CO·전류를 고름 (가스 주의 때 전류는 평소 값 18 A 근처)
    trends: [
      { sensorId: 117, tag: 'CT-17', type: 'CURRENT', unit: 'A', warn: 21.6, alarm: 27, points: usualTrendPoints(18, 0.4, v[117]) },
      { sensorId: 215, tag: 'TE-15', type: 'TEMP', unit: '℃', warn: 160, alarm: 180, points: alarmTrendPointsOf('TEMP', v[215]) },
      { sensorId: 313, tag: 'CO-13', type: 'CO', unit: 'ppm', warn: 20, alarm: 30, points: gasTrendPoints() },
    ],
    causeCandidates: ['버너 공연비 이상 (불완전 연소)', '배기 덕트 댐퍼 막힘', '배기팬 회전수 부족'],
    equipmentType: 'OVEN',
    sensorType: 'CO',
    hazard: 'GAS', // CO → 가스
    processId: 12,
    equipmentId: 31,
    fanAuto: fanAutoOf({ equipmentId: 31, level: LEVEL.WARN, at: raisedAt }),
  }
}

/**
 * '주의 1건 추가'로 넣은 알림(1043, 중도 건조로 버너실 CO-12 22 ppm)의 주의 상세
 * - 가스 주의 상세와 같은 모양에 위치·센서 이름만 바꿈 (상단 바 목록에서 골라 여는 흐름 확인용)
 */
function extraAlarmDetail(e) {
  const d = gasAlarmDetail(e)
  const tags = { 'CT-17': 'CT-16', 'TE-15': 'TE-14', 'CO-13': 'CO-12' }
  return {
    ...d,
    id: e.id,
    location: '울산공장 · 5공장 · 도장 공정 · 중도 건조로 버너실',
    rule: { ...d.rule, sensorTag: 'CO-12' },
    sensors: d.sensors.map((s) => ({ ...s, tag: tags[s.tag], value: s.type === 'CO' ? 22 : s.value })),
    trend: { ...d.trend, sensorId: 312, tag: 'CO-12' },
    trends: d.trends.map((t) => ({ ...t, tag: tags[t.tag] })),
    equipmentId: 29,
    fanAuto: null,
  }
}

/** 가스 주의 추이 (전류 추이 시각을 그대로 쓰고 값만 CO 로) */
function gasTrendPoints() {
  const pts = alarmTrendPoints()
  const n = pts.length
  return pts.map(([t], k) => [t, k < n - 8 ? 6 + ((k * 7) % 3) : Math.round(6 + ((k - (n - 8)) / 7) * 18)])
}

/** 평소 값 근처에서 조금씩 흔들리는 추이 (마지막 값은 last) */
function usualTrendPoints(base, amp, last) {
  const pts = alarmTrendPoints()
  return pts.map(([t], k) => [t, k === pts.length - 1 ? last : Math.round((base + Math.sin(k * 1.3) * amp) * 10) / 10])
}

/* ───────── GET /checklists/{id} ───────── */
export function checklistView(checklistId) {
  const c = scenario.checklist
  if (!c || c.id !== Number(checklistId)) return null
  const memoIndex = c.memo ? 1 : -1 // 현장 메모는 2번째 항목을 마칠 때 남김
  return {
    id: c.id,
    alarmId: scenario.alarm.id,
    status: c.status,
    level: LEVEL.WARN,
    processId: 12,
    equipmentId: 31,
    // 가스 주의면 버너실 CO-13, 아니면 배기팬 전류 CT-17
    ...(scenario.alarm?.kind === 'GAS'
      ? { equipmentName: '상도 건조로 버너실', sensorTag: 'CO-13', valueAtAlarm: 24, unit: 'ppm' }
      : { equipmentName: '상도 건조로 배기팬', sensorTag: 'CT-17', valueAtAlarm: 22.4, unit: 'A' }),
    assignee: { id: c.assigneeId, name: workerName(c.assigneeId) },
    // 진행 수 — 서버 목록(GET /checklists) 줄은 items 없이 이 두 값만 줌 (ChecklistService.listRow)
    doneCount: c.doneTimes.length,
    totalCount: c.items.length,
    sentBy: c.sentBy,
    sentAt: todayAt(c.sentAt),
    remindCount: c.remindCount,
    remindAvailableAt: c.lastRemindMs ? new Date(c.lastRemindMs + 30_000).toISOString() : null,
    items: c.items.map((content, i) => ({
      id: 500 + i + 1,
      seq: i + 1,
      content,
      doneAt: c.doneTimes[i] ? todayAt(c.doneTimes[i]) : null,
      // 담당자 앱에서 체크한 항목은 그 결과·메모 (c.results), 흉내로 진행한 항목은 '이상 없음'
      result: c.doneTimes[i] ? (c.results?.[i]?.result ?? '이상 없음') : null,
      memo: c.results?.[i] ? c.results[i].memo : i === memoIndex ? c.memo.text : null,
    })),
    reportMemo: c.reportMemo ?? null,
    reportedAt: c.reportedAt && todayAt(c.reportedAt),
    confirmedAt: c.confirmedAt && todayAt(c.confirmedAt),
    // 서버 지금 시각 (명세서에 없음): '경과 시간'을 서버 시계 기준으로 세려고. 예시 데이터는 예시 시계
    serverTime: todayAt(scenarioNow()),
  }
}

/* ───────── 경보·화재 (명세서 C4 · C4F · C5, 백엔드 AlarmService.response 필드) ───────── */

const STEP_LABELS = ['버너 연료 차단', '컨베이어 정지', '배기팬 추가 가동 후 정지']

/** 셧다운 진행 (응답에 붙여 줌, 명세서에 없음 — 실제는 SSE command.result 로 옴) */
function shutdownView() {
  const sd = scenario.shutdown
  if (!sd) return null
  return {
    equipmentIds: [...sd.equipmentIds], // 반응형 배열을 그대로 넘기면 structuredClone 이 못 복사함
    auto: !!sd.auto,
    startedAt: todayAt(sd.startedAt),
    steps: STEP_LABELS.map((label, i) => ({ label, at: sd.steps[i] ? todayAt(sd.steps[i]) : null })),
    done: sd.steps.every(Boolean),
  }
}

/**
 * 경보 대응 창 내용: GET /alarms/{id}/response
 * - 경보(1051) 와 화재(1052) 모두 같은 응답 모양, 화재면 fireId 가 있음
 */
export function alarmResponse(alarmId) {
  const id = Number(alarmId)
  const inc = scenario.incident
  if (!inc || (id !== 1051 && id !== 1052)) return null
  const fire = id === 1052 && scenario.fire
  const sensors = sensorsNow()
  const top = sensors.filter((s) => s.equipmentId === 31)
  const t = THERMAL_BY_STATE[scenario.state] ?? THERMAL_BY_STATE[LEVEL.ALARM]
  const logs = mockEquipmentEvents(31)
    .filter((e) => e.type !== 'RECORD' && e.type !== 'THRESHOLD')
    .slice(0, 8)
    .map((e) => ({ at: e.at, text: `${e.text}`, detail: e.detail, level: e.level ?? null, type: e.type }))
  return {
    id,
    level: fire ? LEVEL.FIRE : LEVEL.ALARM,
    hazard: fire ? 'FIRE' : 'GAS', // 경보 예시는 CO 경보 기준 초과 → 가스
    status: fire ? (scenario.fire.status === 'CLEARED' ? 'CLOSED' : 'OPEN') : inc.status,
    fireId: fire ? scenario.fire.id : null,
    title: fire ? '상도 건조로 버너실에서 화재가 감지되었습니다' : '상도 건조로에서 가스 경보가 발생했습니다',
    location: '울산공장 · 5공장 · 도장 공정 · 상도 건조로' + (fire ? ' 버너실' : ''),
    raisedAt: todayAt(fire ? scenario.fire.detectedAt : inc.raisedAt),
    reason: fire
      ? '열화상 최고 온도가 화재 기준(150 ℃)을 3초 이상 넘었습니다. 온도 212 ℃ · CO 120 ppm 동시 상승'
      : '배기팬 과부하로 배기량이 줄어 버너실 CO가 경보 기준을 넘었습니다.',
    reasonDetail: fire ? '' : '전류 27.5 A (평소의 153%) · CO 34 ppm · 온도 164 ℃ 상승 중', // 명세서에 없음 (사유 아래 한 줄)
    cameraId: 301,
    thermal: { imageUrl: t.image, max: t.max, min: t.min, avg: t.avg, maxPoint: t.point, scale: t.scale, zoneName: fire ? '버너실 앞' : '배기팬 모터부', cameraTag: 'IR-01' },
    // 상도 건조로 센서 3종 (명세서는 공정 센서 전부 — 설계안은 3종만)
    sensors: top.map((s) => ({
      id: s.id, tag: s.tag, type: s.type, equipmentName: '상도 건조로', value: s.value, unit: s.unit, warn: s.warn, alarm: s.alarm, level: s.level,
    })),
    equipment: EQUIPMENT.filter((e) => e.seq).map((e) => ({
      id: e.id,
      name: e.name,
      tags: sensors.filter((s) => s.equipmentId === e.id).map((s) => s.tag).join(' · '),
      level: equipmentLevel(e.id, sensors),
      runState: runStateOf(e.id),
    })),
    logs,
    shutdown: shutdownView(),
    // 가스(CO) 경보 때 배기팬 자동 제어 결과 (100%) — 화재 창에서는 쓰지 않음 (화재는 자동 셧다운)
    fanAuto: fire ? null : fanAutoOf({ equipmentId: 31, level: LEVEL.ALARM, at: todayAt(inc.raisedAt) }),
    valueStillOver: sensors.some((s) => s.level === LEVEL.ALARM),
  }
}

/* ───────── 화재: GET /fires, GET /fires/{id} ───────── */
export function fireView() {
  const f = scenario.fire
  if (!f) return null
  const t = THERMAL_BY_STATE[LEVEL.FIRE]
  const done = (time) => ({ at: todayAt(time), done: true })
  return {
    id: f.id,
    alarmId: 1052,
    status: f.status,
    location: '울산공장 · 5공장 · 도장 공정 · 상도 건조로 버너실',
    siteId: 1,
    siteName: '울산공장',
    factoryId: 5,
    processId: 12,
    equipmentId: 31,
    lat: 35.5438,
    lng: 129.3697,
    detectedAt: todayAt(f.detectedAt),
    basis: '열화상 최고 온도가 화재 기준(150 ℃)을 3초 이상 넘었습니다. 온도 212 ℃ · CO 120 ppm 동시 상승',
    values: { thermalMax: t.max, temp: 212, co: 120 }, // 명세서에 없음 (감지 근거 숫자 칸)
    thermal: { cameraId: 301, cameraTag: 'IR-01', imageUrl: t.image, max: t.max, min: t.min, avg: t.avg, maxPoint: t.point, scale: t.scale },
    broadcast: {
      on: f.status === 'ACTIVE',
      startedAt: todayAt(designAt('16:58:05')), // 설계안 시각 → 지금 상황 기준
      message: '5공장 도장 공정에서 화재가 발생했습니다. 작업자는 즉시 지정 대피로로 대피하십시오.',
    },
    hqNotifiedAt: todayAt(designAt('16:58:06')),
    unlock: { failCount: f.failCount, maxFail: 5, locked: f.locked },
    actions: [
      { step: 'DETECTED', ...done(f.detectedAt), text: '화재 감지' },
      { step: 'BROADCAST', ...done(designAt('16:58:05')), text: '공장 내 경고 방송 송출', running: f.status === 'ACTIVE' },
      { step: 'SHUTDOWN', ...done(designAt('16:58:06')), text: '상도 건조로 자동 셧다운 (버너 연료 차단)' },
      { step: 'HQ_NOTIFY', ...done(designAt('16:58:06')), text: '본사 관제 통보' },
      { step: 'WORKER_PUSH', ...done(designAt('16:58:06')), text: '도장 공정 담당자 앱 알림' },
      f.status === 'CLEARED'
        ? { step: 'CLEARED', at: todayAt(f.clearedAt), done: true, text: `경보 해제 · ${f.clearedBy}` }
        : { step: 'CLEARED', at: null, done: false, text: '공장 담당자 경보 해제 (비밀번호 확인 필요)' },
    ],
    clearedAt: f.clearedAt && todayAt(f.clearedAt),
    clearedBy: f.clearedBy,
    serverTime: todayAt(scenarioNow()),
  }
}
