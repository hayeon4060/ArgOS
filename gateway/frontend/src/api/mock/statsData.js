/**
 * 통계·이력 관리 예시 데이터 (화면설계안 v-pages.js vStats() / statRows(), 백엔드 stats/StatsService)
 *
 * 한 가지 원본에서 모두 계산함 → 요약·그래프·표·칸 차트·내보내기 숫자가 항상 서로 맞음
 *  1) 알림 목록 한 벌을 만든다 (GET /alarms 의 items 와 같은 모양, 백엔드 AlarmViews)
 *     - 오늘: 지금 예시 상황의 알림(mockAlarms) 그대로 → 상단 바·알림 목록과 같은 건이 '조치 중'으로 보임
 *     - 어제부터 29일 전: 설계안의 일별 건수(W30 · D30) 그대로
 *     - 그 이전 1년: 비슷한 수준으로 고정 난수 (직전 기간 대비 · 직접 선택 기간용)
 *  2) 조건(기간·공장·공정·센서·등급·근무조)으로 거른 뒤 summary / trend / by-process / alarms 를 셈
 *     (백엔드 StatsService 와 같은 방식)
 *
 * 5공장만 지난 기록이 있음 (설계안도 5공장·전체 공장 말고는 '조건에 맞는 알림이 없습니다')
 */
import { mockAlarms, mockSiteTrees, todayAt } from './data'
import { scenarioNow } from './scenario'
import { MAX_RANGE_DAYS } from '@/features/history/historyFilter'

/* ───────── 고정 난수 (새로고침해도 같은 값) ───────── */
let seed = 20261117
const rand = () => {
  seed = (seed * 16807) % 2147483647
  return seed / 2147483647
}
const pick = (list) => list[Math.floor(rand() * list.length)]

/* ───────── 날짜 도우미 (모두 한국 시간 문자열) ───────── */
const pad = (n) => String(n).padStart(2, '0')
/** Date → 'YYYY-MM-DD' (PC 시간 기준. toISOString 은 오전 9시 전에 어제가 되므로 쓰지 않음) */
export const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const iso = (d) => `${ymd(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}+09:00`
const dayOf = (isoText) => isoText.slice(0, 10)
const hourOf = (isoText) => Number(isoText.slice(11, 13))
/** 'YYYY-MM-DD' → 그날 0시 Date */
const parseDay = (text) => {
  const [y, m, d] = text.split('-').map(Number)
  return new Date(y, m - 1, d)
}
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n)

/* ───────── 5공장 설비 · 센서 (설계안 statRows 의 설비 이름) ───────── */
// [공정 id, 공정 이름, 설비 이름, 설비 상세 id(도장 공정만), 태그, 종류, 단위, 주의 기준, 경보 기준]
const POOL = [
  [12, '도장 공정', '상도 건조로 배기팬', 31, 'CT-17', 'CURRENT', 'A', 21.6, 27],
  [12, '도장 공정', '전처리 순환펌프', 24, 'CT-11', 'CURRENT', 'A', 12, 15],
  [12, '도장 공정', '상도 부스 급기팬', 30, 'CT-16', 'CURRENT', 'A', 19, 23],
  [12, '도장 공정', '전착 건조로', 26, 'TE-13', 'TEMP', '℃', 160, 180],
  [12, '도장 공정', '소각설비', 34, 'TE-21', 'TEMP', '℃', 800, 850],
  [12, '도장 공정', '중도 건조로 버너실', 29, 'CO-12', 'CO', 'ppm', 20, 30],
  [12, '도장 공정', '상도 건조로 버너실', 31, 'CO-13', 'CO', 'ppm', 20, 30],
  [11, '차체 공정', '스폿 용접 2라인', null, 'CT-08', 'CURRENT', 'A', 30, 36],
  [11, '차체 공정', '스폿 용접 1라인', null, 'TE-07', 'TEMP', '℃', 70, 80],
  [11, '차체 공정', '하부 실러 부스', null, 'CO-05', 'CO', 'ppm', 20, 30],
  [10, '프레스 공정', '유압 유닛 2호', null, 'CT-04', 'CURRENT', 'A', 24, 29],
  [10, '프레스 공정', '유압 유닛 1호', null, 'TE-03', 'TEMP', '℃', 65, 75],
  [13, '의장 공정', '컨베이어 구동부', null, 'CT-31', 'CURRENT', 'A', 16, 20],
  [13, '의장 공정', '검사 라인 배기팬', null, 'CT-34', 'CURRENT', 'A', 11.5, 14],
  [13, '의장 공정', '도장 보수 부스', null, 'TE-32', 'TEMP', '℃', 60, 70],
  [13, '의장 공정', '실러 도포실', null, 'CO-33', 'CO', 'ppm', 20, 30],
]

// 설계안 '공정별 알림' 30일 합계 비율 (공정 × 센서 종류) → 어느 설비에서 날지 고를 때 무게
const WEIGHT = {
  12: { CURRENT: 14, TEMP: 12, CO: 9 },
  11: { CURRENT: 9, TEMP: 13, CO: 2 },
  10: { CURRENT: 11, TEMP: 5, CO: 0 },
  13: { CURRENT: 6, TEMP: 4, CO: 1 },
}
const SLOTS = Object.entries(WEIGHT).flatMap(([pid, byType]) =>
  Object.entries(byType).flatMap(([type, n]) => Array(n).fill([Number(pid), type])),
)

// 설계안 일별 건수 (29일 전 → 오늘). 오늘 칸은 지금 상황의 알림으로 대신함
const W30 = [3, 2, 3, 1, 2, 3, 3, 3, 3, 3, 1, 3, 2, 3, 3, 3, 3, 2, 2, 3, 1, 3, 2, 2, 2, 2, 2, 3, 2, 4]
const D30 = [0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0, 2, 1, 0, 0, 1, 1, 0, 0]

const WORKERS = ['김도현', '박성훈', '이재호', '최민규', '정우진', '조현우']
const ADMINS = ['한지우', '윤서연', '오승민']

/** 시간대 고르기: 낮(8~19시)에 많고 14시 반쯤 가장 많음, 밤은 드묾 (설계안 히트맵 모양) */
function pickHour() {
  if (rand() < 0.08) return pick([0, 2, 4, 5, 21, 22, 23])
  for (;;) {
    const h = 7 + Math.floor(rand() * 13)
    if (rand() < Math.exp(-(((h - 14.5) / 3.2) ** 2)) + 0.12) return h
  }
}

/** 지난 알림 한 건 (모두 조치 완료 CLOSED) */
function pastAlarm(id, day, isAlarm) {
  const [pid, type] = pick(SLOTS)
  const p = pick(POOL.filter((x) => x[0] === pid && x[5] === type))
  const [processId, processName, equipmentName, equipmentId, tag, , unit, warn, alarm] = p
  const threshold = isAlarm ? alarm : warn
  const raised = new Date(day.getFullYear(), day.getMonth(), day.getDate(), pickHour(), Math.floor(rand() * 60), Math.floor(rand() * 60))
  const minutes = isAlarm ? 18 + Math.floor(rand() * 17) : 7 + Math.floor(rand() * 15)
  const closed = new Date(raised.getTime() + minutes * 60000)
  const total = 3 + Math.floor(rand() * 3)
  // 체크리스트 100건 중 3건쯤은 다 끝내지 못하고 닫힘 (설계안 '미완료 3건')
  const done = rand() < 0.04 ? total - 1 : total
  const value = Math.round(threshold * (1 + rand() * (isAlarm ? 0.06 : 0.08)) * 10) / 10
  return {
    id,
    level: isAlarm ? 'ALARM' : 'WARN',
    status: 'CLOSED',
    title: `${{ CURRENT: '전류', TEMP: '온도', CO: 'CO' }[type]} ${isAlarm ? '경보' : '주의'} 해제`,
    location: `5공장 · ${processName} · ${equipmentName}`,
    siteId: 1,
    factoryId: 5,
    processId,
    equipmentId,
    sensorId: null,
    sensorTag: tag,
    sensorType: type,
    value: unit === '℃' ? Math.round(value) : value,
    unit,
    threshold,
    raisedAt: iso(raised),
    ackedBy: pick(ADMINS),
    ackedAt: iso(new Date(raised.getTime() + 60000)),
    closedAt: iso(closed),
    // 경보는 체크리스트 대신 설비 셧다운으로 조치 (설계안 '설비 셧다운')
    checklist: isAlarm ? null : { id: id + 100000, status: 'CONFIRMED', doneCount: done, totalCount: total },
    assignee: isAlarm ? null : pick(WORKERS),
    actionMinutes: minutes,
  }
}

/** 지난 1년 + 직전 기간용 알림 (오늘 제외). 한 번만 만듦 */
let pastCache = null
function pastAlarms() {
  if (pastCache) return pastCache
  const today = parseDay(ymd(new Date()))
  const list = []
  let id = 900000
  for (let back = 1; back <= 400; back += 1) {
    const day = addDays(today, -back)
    // 최근 29일은 설계안 일별 건수, 그 전은 비슷한 수준의 고정 난수
    const warnN = back <= 29 ? W30[29 - back] : pick([1, 2, 2, 3, 3, 3, 4])
    const alarmN = back <= 29 ? D30[29 - back] : pick([0, 0, 0, 0, 1, 1])
    for (let i = 0; i < warnN; i += 1) list.push(pastAlarm(id--, day, false))
    for (let i = 0; i < alarmN; i += 1) list.push(pastAlarm(id--, day, true))
  }
  pastCache = list
  return list
}

// 오늘 알림의 설비 이름 → 설비 상세 id (오늘 알림 예시 데이터의 equipmentId 는 화면용 번호가 아님)
const EQUIPMENT_BY_NAME = Object.fromEntries(POOL.filter((p) => p[3]).map((p) => [p[2], p[3]]))

/** 오늘 예시 알림 중 공정 id 가 비어 있는 다른 공장 알림 → 위치의 공정 이름으로 사업장 트리에서 찾음 */
function processIdOf(a, trees) {
  const [, processName] = a.location.split(' · ')
  const factory = trees[a.siteId]?.factories.find((f) => f.id === a.factoryId)
  const list = factory?.processes ?? []
  // 이름이 같은 공정, 없으면 앞 단어가 같은 공정 ('가공 라인' → '가공 공정')
  const head = (processName ?? '').split(' ')[0]
  return (list.find((p) => p.name === processName) ?? list.find((p) => head && p.name.startsWith(head)))?.id ?? null
}

/** 오늘 알림 (지금 예시 상황) + 지난 알림, 최근 것이 앞 */
function allAlarms() {
  const trees = mockSiteTrees()
  const today = mockAlarms().map((a) => {
    const name = a.location.split(' · ').at(-1)
    const row = { ...a, equipmentId: EQUIPMENT_BY_NAME[name] ?? null, processId: a.processId ?? processIdOf(a, trees) }
    // 오늘 이미 끝난 예시 알림은 해제 시각·체크리스트가 비어 있음 → 지난 알림과 같은 모양으로 채움
    if (row.status === 'CLOSED' && !row.closedAt) {
      const minutes = 8 + (row.id % 9)
      row.closedAt = iso(new Date(new Date(row.raisedAt).getTime() + minutes * 60000))
      row.checklist = row.checklist ?? { id: row.id + 100000, status: 'CONFIRMED', doneCount: 4, totalCount: 4 }
    }
    row.actionMinutes = row.closedAt ? Math.max(1, Math.round((new Date(row.closedAt) - new Date(row.raisedAt)) / 60000)) : null
    return row
  })
  // 유형(hazard): 백엔드 alarm.hazard 와 같은 값. CO → 가스(GAS), 전류·온도·열화상 → 화재(FIRE)
  return [...today, ...pastAlarms()]
    .map((a) => (a.hazard ? a : { ...a, hazard: a.sensorType === 'CO' ? 'GAS' : 'FIRE' }))
    .sort((a, b) => b.raisedAt.localeCompare(a.raisedAt))
}

/** 알림 한 건 찾기 (상세 패널 예시 데이터용) */
export const findStatsAlarm = (id) => allAlarms().find((a) => a.id === Number(id)) ?? null

/** 센서 태그 → [주의 기준, 경보 기준] (예시 설비 목록에 있는 태그만, 없으면 null) */
export function ruleOfTag(tag) {
  const p = POOL.find((x) => x[4] === tag)
  return p ? [p[7], p[8]] : null
}

/* ───────── 조건으로 거르기 (백엔드 AlarmCriteria.matches) ───────── */
const ACTIVE = ['OPEN', 'ACKED', 'ACTING']
/** 주간 07:00 ~ 18:59, 나머지 야간 */
export const isDayShift = (isoText) => hourOf(isoText) >= 7 && hourOf(isoText) < 19

function filtered(c) {
  return allAlarms().filter(
    (a) =>
      dayOf(a.raisedAt) >= c.from &&
      dayOf(a.raisedAt) <= c.to &&
      (!c.siteId || a.siteId === Number(c.siteId)) &&
      (!c.factoryId || a.factoryId === Number(c.factoryId)) &&
      (!c.processId || a.processId === Number(c.processId)) &&
      (!c.sensorType || a.sensorType === c.sensorType) &&
      (!c.level || a.level === c.level) &&
      (!c.hazard || a.hazard === c.hazard) &&
      (!c.shift || (c.shift === 'DAY') === isDayShift(a.raisedAt)),
  )
}

/** 같은 길이의 바로 앞 기간 */
function previous(c) {
  const days = daysOf(c)
  const from = parseDay(c.from)
  return { ...c, from: ymd(addDays(from, -days)), to: ymd(addDays(from, -1)) }
}
const daysOf = (c) => Math.round((parseDay(c.to) - parseDay(c.from)) / 86400000) + 1

/** 기간이 30일을 넘으면 백엔드처럼 400 RANGE_TOO_LARGE */
export function rangeError(c) {
  return daysOf(c) > MAX_RANGE_DAYS
    ? { code: 'RANGE_TOO_LARGE', message: `한 번에 ${MAX_RANGE_DAYS}일까지 조회할 수 있습니다.`, status: 400 }
    : null
}

/* ───────── GET /stats/summary ───────── */
function numbers(list) {
  const closed = list.filter((a) => a.actionMinutes != null)
  const checklists = list.map((a) => a.checklist).filter(Boolean)
  const done = checklists.reduce((s, c) => s + c.doneCount, 0)
  const total = checklists.reduce((s, c) => s + c.totalCount, 0)
  return {
    count: list.length,
    danger: list.filter((a) => a.level === 'ALARM' || a.level === 'FIRE').length,
    avg: closed.length ? Math.round((closed.reduce((s, a) => s + a.actionMinutes, 0) / closed.length) * 10) / 10 : null,
    rate: total ? Math.round((done * 1000) / total) / 10 : null,
    // 추가 필드: 다 끝내지 못한 체크리스트 수 (요약 '미완료 n건')
    notDone: checklists.filter((c) => c.doneCount < c.totalCount && c.status === 'CONFIRMED').length,
  }
}
const metric = (now, before) => ({
  value: now,
  diff: now == null || before == null ? null : Math.round((now - before) * 10) / 10,
})

export function summary(c) {
  const a = numbers(filtered(c))
  const b = numbers(filtered(previous(c)))
  return {
    alarmCount: metric(a.count, b.count),
    dangerCount: metric(a.danger, b.danger),
    avgActionMinutes: metric(a.avg, b.avg),
    checklistDoneRate: metric(a.rate, b.rate),
    checklistNotDoneCount: a.notDone,
  }
}

/* ───────── GET /stats/trend ───────── */
/** ISO 주 번호 'YYYY-Www' (백엔드 IsoFields.WEEK_OF_WEEK_BASED_YEAR) */
function weekKey(d) {
  const t = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 3 - ((d.getDay() + 6) % 7))
  const week1 = new Date(t.getFullYear(), 0, 4)
  const n = 1 + Math.round(((t - week1) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7)
  return `${t.getFullYear()}-W${pad(n)}`
}
const keyOf = (isoText, unit) =>
  unit === 'HOUR' ? `${isoText.slice(0, 13)}:00` : unit === 'WEEK' ? weekKey(parseDay(dayOf(isoText))) : dayOf(isoText)

export function trend(c, unit) {
  const days = daysOf(c)
  const u = unit || (days <= 1 ? 'HOUR' : days <= 62 ? 'DAY' : 'WEEK')
  // 빈 칸도 0 으로 채움 (백엔드와 같음)
  const buckets = new Map()
  const start = parseDay(c.from)
  const end = addDays(parseDay(c.to), 1)
  for (let t = start; t < end; ) {
    buckets.set(keyOf(iso(t), u), { warn: 0, alarm: 0, day: 0, night: 0, minutes: [] })
    t = u === 'HOUR' ? new Date(t.getTime() + 3600000) : addDays(t, u === 'WEEK' ? 7 : 1)
  }
  for (const a of filtered(c)) {
    const k = keyOf(a.raisedAt, u)
    if (!buckets.has(k)) buckets.set(k, { warn: 0, alarm: 0, day: 0, night: 0, minutes: [] })
    const b = buckets.get(k)
    b[a.level === 'WARN' ? 'warn' : 'alarm'] += 1
    b[isDayShift(a.raisedAt) ? 'day' : 'night'] += 1
    if (a.actionMinutes != null) b.minutes.push(a.actionMinutes)
  }
  return {
    unit: u,
    points: [...buckets].map(([key, b]) => ({
      key,
      warn: b.warn,
      alarm: b.alarm,
      day: b.day,
      night: b.night,
      // 추가 필드: 그 칸의 평균 조치 시간 (요약 '평균 조치 시간' 작은 그래프)
      avgActionMinutes: b.minutes.length ? Math.round(b.minutes.reduce((s, m) => s + m, 0) / b.minutes.length) : null,
    })),
  }
}

/* ───────── GET /stats/by-process ───────── */
export function byProcess(c) {
  const rows = new Map()
  for (const a of filtered(c)) {
    if (!a.processId) continue
    if (!rows.has(a.processId)) {
      rows.set(a.processId, { processId: a.processId, processName: a.location.split(' · ')[1], CURRENT: 0, TEMP: 0, CO: 0, IR: 0 })
    }
    rows.get(a.processId)[a.sensorType] += 1
  }
  return [...rows.values()].map((r) => ({ ...r, total: r.CURRENT + r.TEMP + r.CO + r.IR })).sort((a, b) => b.total - a.total)
}

/* ───────── GET /stats/alarms ───────── */
/**
 * @param {object} c 조건 + status(추가 요청: ACTIVE | CLOSED), sort, page(0부터), size
 */
export function alarms(c) {
  let list = filtered(c)
  if (c.status === 'ACTIVE') list = list.filter((a) => ACTIVE.includes(a.status))
  if (c.status === 'CLOSED') list = list.filter((a) => a.status === 'CLOSED')
  if (c.sort === 'actionMinutes,desc') list = [...list].sort((a, b) => (b.actionMinutes ?? -1) - (a.actionMinutes ?? -1))
  const page = Math.max(0, c.page ?? 0)
  const size = Math.max(1, Math.min(500, c.size ?? 20))
  // serverTime: 조치 중 '경과' 분을 서버 시계로 셈 (추가 요청 — 예시는 예시 시계)
  return { items: list.slice(page * size, page * size + size), page, size, total: list.length, serverTime: todayAt(scenarioNow()) }
}

/* ───────── GET /stats/export (알림 이력 상세 CSV — 열은 docs/stats-history-api.md 와 같음) ───────── */
const CSV_HEAD = '알림 번호,발생 시각,해제 시각,유형,공장,공정,설비,등급,알림 센서,센서 종류,발생 값,기준값,단위,상태,확인자,조치,조치 담당자,체크리스트,조치 시간(분)'
const csvCell = (v) => {
  if (v == null) return ''
  const s = String(v)
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
const csvTime = (isoText) => (isoText ? `${isoText.slice(0, 10)} ${isoText.slice(11, 19)}` : '')
const HAZARD_NAME = { FIRE: '화재', GAS: '가스' }
const SENSOR_NAME = { CURRENT: '전류', TEMP: '온도', CO: 'CO', IR: '열화상' }
const STATUS_NAME = { OPEN: '확인 대기', ACKED: '확인함', ACTING: '조치 중', CLOSED: '해제' }

export function exportCsv(c) {
  const list = filtered(c)
  if (list.length > 50000) return { error: { code: 'EXPORT_TOO_LARGE', message: '5만 건을 넘습니다. 기간을 줄여 주세요.', status: 400 } }
  const lines = list.map((a) => {
    const [factory, process, ...rest] = a.location.split(' · ')
    return [
      a.id, csvTime(a.raisedAt), csvTime(a.closedAt), HAZARD_NAME[a.hazard], factory, process, rest.join(' · '),
      a.level === 'WARN' ? '주의' : '경보', a.sensorTag, SENSOR_NAME[a.sensorType], a.value, a.threshold, a.unit,
      STATUS_NAME[a.status], a.ackedBy, a.checklist ? '체크리스트' : '설비 셧다운', a.assignee,
      a.checklist ? `${a.checklist.doneCount}/${a.checklist.totalCount}` : '', a.actionMinutes,
    ].map(csvCell).join(',')
  })
  return {
    data: {
      fileName: `알림이력_${c.from.replace(/-/g, '')}-${c.to.replace(/-/g, '')}.csv`,
      // UTF-8 BOM (엑셀에서 한글이 깨지지 않게)
      text: `\ufeff${CSV_HEAD}\n${lines.join('\n')}\n`,
      rows: list.length,
    },
  }
}

/* ───────── 월별 보고서 (명세서 GET /reports/monthly — 백엔드에 아직 없음) ───────── */
/** 고를 수 있는 달 (지난달까지만, 이번 달은 '작성 중') */
export function monthlyReports() {
  const now = new Date()
  return Array.from({ length: 5 }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const month = `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
    const next = new Date(d.getFullYear(), d.getMonth() + 1, 1)
    return { month, ready: i > 0, issuedAt: i > 0 ? ymd(next) : null, willIssueAt: ymd(next) }
  })
}

/** 보고서 미리보기 (설계안 statReport() 의 첫 쪽 내용) */
export function monthlyReport(month) {
  const from = `${month}-01`
  const [y, m] = month.split('-').map(Number)
  const to = ymd(new Date(y, m, 0))
  const now = numbers(filtered({ from, to, siteId: 1 }))
  const prevFrom = ymd(new Date(y, m - 2, 1))
  const prevTo = ymd(new Date(y, m - 1, 0))
  const before = numbers(filtered({ from: prevFrom, to: prevTo, siteId: 1 }))
  return {
    month,
    siteName: '울산공장',
    issuedAt: ymd(new Date(y, m, 1)),
    sign: { writer: '시스템', reviewer: '한지우', approver: '서지훈' },
    kpi: {
      alarmCount: now.count,
      alarmDiff: now.count - before.count,
      dangerCount: now.danger,
      dangerDiff: now.danger - before.danger,
      avgActionMinutes: now.avg,
      checklistDoneRate: now.rate,
    },
    // 공장별 안전 등급 (설계안 예시값 — 등급 기준은 확인 필요, 5공장 숫자만 실제 집계)
    grades: [
      { factory: '1공장', grade: 'A', score: 96, warn: 2, alarm: 0, avgActionMinutes: 9 },
      { factory: '2공장', grade: 'A', score: 94, warn: 4, alarm: 0, avgActionMinutes: 11 },
      { factory: '3공장', grade: 'A', score: 97, warn: 1, alarm: 0, avgActionMinutes: 8 },
      { factory: '4공장', grade: 'B', score: 88, warn: 6, alarm: 1, avgActionMinutes: 14 },
      { factory: '5공장', grade: now.danger ? 'B' : 'A', score: now.danger ? 86 : 92, warn: now.count - now.danger, alarm: now.danger, avgActionMinutes: Math.round(now.avg ?? 0) },
      { factory: '엔진·변속기 공장', grade: 'A', score: 93, warn: 3, alarm: 0, avgActionMinutes: 10 },
    ],
    // 주요 경보 (그달 경보 중 최근 3건)
    major: filtered({ from, to, siteId: 1, level: 'ALARM' })
      .slice(0, 3)
      .map((a) => ({ at: a.raisedAt, location: a.location, text: a.title.replace(' 해제', ''), action: '설비 셧다운' })),
  }
}
