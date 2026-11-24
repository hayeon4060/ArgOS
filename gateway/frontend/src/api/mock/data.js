/**
 * 예시 데이터 (백엔드 없이 화면 확인용)
 * - 응답 모양: 설계 자료 API 명세서(_design/ArgOS_API명세서.html)의 응답 예시와 같게
 * - 상황(정상·주의 등)에 따라 바뀌는 값은 함수로 만들어, 부를 때마다 지금 예시 상황(scenario.js)으로 계산
 *   → 사업장 선택·사업장 현황·공장·공정·설비·상단 바 숫자가 항상 같음
 * - 바뀌는 곳은 5공장 도장 공정 상도 건조로 센서 3개(processData.js)와 그 알림(아래 mockAlarms) 뿐,
 *   나머지 공장·공정은 늘 정상
 */
import { addedFactoriesOf, ADDED, extraProcessesOf } from './addedScope'
import { fanAutoOf } from './fanAuto'
import { maxLevel, sensorsNow } from './processData'
import { activeChecklist, designAt, scenario, workerName } from './scenario'
import { LEVEL, ROLE } from '@/types/codes'

const { NORMAL } = LEVEL

/* ───────── 로그인 (POST /auth/login) ───────── */

/**
 * 예시 로그인 계정 (비밀번호는 모두 argos1234)
 * - 실제 서비스 계정이 아님. 백엔드가 생기면 이 목록은 쓰지 않음
 * - scope: 담당 범위 (siteId, factoryIds, processIds)
 */
export const MOCK_PASSWORD = 'argos1234'

export const MOCK_USERS = [
  {
    id: 1,
    loginId: 'hq_admin',
    name: '서지훈',
    role: ROLE.HQ_ADMIN,
    scope: { siteId: null, factoryIds: [], processIds: [] },
  },
  {
    id: 7,
    loginId: 'ulsan5_admin',
    name: '한지우',
    role: ROLE.PLANT_ADMIN,
    scope: { siteId: 1, factoryIds: [5], processIds: [] },
  },
  // 공정 담당자: 담당자 앱(phone)으로만 로그인 (id 는 scenario.js WORKERS 와 같게)
  {
    id: 21,
    loginId: 'u5_paint01',
    name: '김도현',
    role: ROLE.WORKER,
    scope: { siteId: 1, factoryIds: [5], processIds: [12] },
  },
  {
    id: 22,
    loginId: 'u5_paint02',
    name: '박성훈',
    role: ROLE.WORKER,
    scope: { siteId: 1, factoryIds: [5], processIds: [12] },
  },
]

/* ───────── 공통 ───────── */

/** 센서 단계별 개수 { NORMAL, WARN, ALARM } */
const levels = (normal, warn = 0, alarm = 0) => ({ [LEVEL.NORMAL]: normal, [LEVEL.WARN]: warn, [LEVEL.ALARM]: alarm })

/** 센서 종류별 단계 개수 (전류·온도·CO) — 숫자 하나면 모두 정상, 배열이면 [정상, 주의, 경보] */
const byType = (current, temp, co) => {
  const toLevels = (v) => (Array.isArray(v) ? levels(...v) : levels(v))
  return { CURRENT: toLevels(current), TEMP: toLevels(temp), CO: toLevels(co) }
}

/** 종류별 개수 여러 개 더하기 (공정 → 공장 → 사업장) */
function sumByType(list) {
  const sum = (type, level) => list.reduce((n, item) => n + item[type][level], 0)
  const one = (type) => levels(sum(type, NORMAL), sum(type, LEVEL.WARN), sum(type, LEVEL.ALARM))
  return { CURRENT: one('CURRENT'), TEMP: one('TEMP'), CO: one('CO') }
}

/** 종류별 개수 → 전체 단계별 개수 */
function totalOf(t) {
  const sum = (level) => t.CURRENT[level] + t.TEMP[level] + t.CO[level]
  return levels(sum(NORMAL), sum(LEVEL.WARN), sum(LEVEL.ALARM))
}

/* ───────── 5공장 공정 (GET /factories/{factoryId}/overview) ─────────
 * 프레스·차체·의장은 늘 정상, 도장 공정은 센서 19개의 지금 단계로 만듦 (processData.js sensorsNow)
 */

/** 도장 공정 열화상 최고 온도 (상황별, 설계안 vEquip() IR 값) */
const THERMAL_MAX = { [NORMAL]: 52.0, [LEVEL.WARN]: 78.4, [LEVEL.ALARM]: 96.2, [LEVEL.FIRE]: 486 }

/** 도장 공정 한 줄 (지금 상황) */
function paintProcess() {
  const sensors = sensorsNow()
  const count = (type, level) => sensors.filter((s) => s.type === type && s.level === level).length
  const typeLevels = (type) => levels(count(type, NORMAL), count(type, LEVEL.WARN), count(type, LEVEL.ALARM))
  // 설비 14대 중 상도 건조로만 상황에 따라 바뀜 (화재 중이면 공정·설비 단계가 '화재', 개수는 경보 칸에 셈)
  const fire = scenario.state === LEVEL.FIRE
  const top = fire ? LEVEL.ALARM : maxLevel(sensors.filter((s) => s.equipmentId === 31).map((s) => s.level))
  return {
    id: 12,
    code: 'PNT',
    name: '도장 공정',
    level: fire ? LEVEL.FIRE : maxLevel(sensors.map((s) => s.level)),
    equipmentCount: 14,
    equipmentLevels: levels(top === NORMAL ? 14 : 13, top === LEVEL.WARN ? 1 : 0, top === LEVEL.ALARM ? 1 : 0),
    sensorCount: sensors.length,
    levelsByType: { CURRENT: typeLevels('CURRENT'), TEMP: typeLevels('TEMP'), CO: typeLevels('CO') },
    abnormal: sensors
      .filter((s) => s.level !== NORMAL)
      .map((s) => ({ sensorId: s.id, tag: s.tag, type: s.type, value: s.value, unit: s.unit, level: s.level })), // 서버 DashboardService.abnormal 과 같은 이름
    thermalMax: THERMAL_MAX[scenario.state],
    hasFloorplan: true,
  }
}

/** 5공장 공정 4개 (설계안 '공정별 설비 상태' 카드 값) */
function plant5Processes() {
  return [
    {
      id: 10, code: 'PRS', name: '프레스 공정', level: NORMAL,
      equipmentCount: 9, equipmentLevels: levels(9), sensorCount: 12, levelsByType: byType(6, 4, 2),
      abnormal: [], thermalMax: 41.2, hasFloorplan: false,
    },
    {
      id: 11, code: 'BDY', name: '차체 공정', level: NORMAL,
      equipmentCount: 12, equipmentLevels: levels(12), sensorCount: 16, levelsByType: byType(8, 5, 3),
      abnormal: [], thermalMax: 46.8, hasFloorplan: false,
    },
    paintProcess(),
    {
      id: 13, code: 'ASM', name: '의장 공정', level: NORMAL,
      equipmentCount: 11, equipmentLevels: levels(11), sensorCount: 14, levelsByType: byType(7, 4, 3),
      abnormal: [], thermalMax: 39.5, hasFloorplan: false,
    },
  ]
}

/** 공장 상세 (공장 ID → 공정 목록, 열화상 카메라). 배치도가 없는 공장은 null */
export function mockFactoryDetail(factoryId) {
  if (Number(factoryId) !== 5) return null
  return {
    processes: plant5Processes(),
    cameras: [
      { id: 301, tag: 'IR-01', processId: 12 },
      { id: 302, tag: 'IR-02', processId: 10 },
      { id: 303, tag: 'IR-03', processId: 11 },
      { id: 304, tag: 'IR-04', processId: 13 },
    ],
  }
}

/* ───────── 사업장 펼치기 (GET /sites/{siteId}/tree) ─────────
 * 사업장 현황(GET /sites/{siteId}/overview)도 이 공장 목록으로 만듦 (api/dashboard.js)
 */

let processId = 100
/** 공정 목록 만들기 (이름 → 모두 정상, 이상 센서 없음) */
const processes = (...names) => names.map((name) => ({ id: processId++, name, level: NORMAL, abnormal: [] }))
const CAR = ['프레스 공정', '차체 공정', '도장 공정', '의장 공정']
const ENGINE = ['가공 공정', '조립 공정']

/**
 * 공장 한 줄 (늘 정상인 공장)
 * - code: 지도 구역과 짝을 맞추는 공장 코드 (명세서 overview 예시의 "P5")
 * - todayAlarmCount: 명세서 tree 응답에는 없지만 화면 '오늘 알림' 칸에 필요해서 추가 (backend-notes.md 에 요청)
 */
const factory = (id, code, name, procNames, types, todayAlarmCount) => ({
  id,
  code,
  name,
  level: NORMAL,
  levelsByType: types,
  processes: processes(...procNames),
  todayAlarmCount,
})

/** 늘 정상인 공장들 (한 번만 만듦 → 공정 id 가 바뀌지 않음) */
const ULSAN_BEFORE_5 = [
  factory(1, 'P1', '1공장', CAR, byType(10, 7, 4), 1),
  factory(2, 'P2', '2공장', CAR, byType(8, 6, 4), 2),
  factory(3, 'P3', '3공장', CAR, byType(9, 7, 4), 0),
  factory(4, 'P4', '4공장', CAR, byType(8, 5, 4), 0),
]
const ULSAN_ENGINE = factory(6, 'EV', '엔진·변속기 공장', ENGINE, byType(8, 5, 4), 1)
const ASAN = [factory(7, 'P1', '승용 1공장', CAR, byType(13, 9, 6), 1), factory(8, 'EN', '엔진공장', ENGINE, byType(8, 6, 4), 0)]
const JEONJU = [
  factory(9, 'C1', '상용 1공장', CAR, byType(10, 7, 5), 0),
  factory(10, 'C2', '상용 2공장', ['차체 공정', '도장 공정', '의장 공정'], byType(7, 5, 4), 0),
]

/** 5공장: 공정 4개를 더해서 만듦 (도장 공정 단계가 공장 단계) */
function plant5() {
  const list = plant5Processes()
  return {
    id: 5,
    code: 'P5',
    name: '5공장',
    level: maxLevel(list.map((p) => p.level)),
    levelsByType: sumByType(list.map((p) => p.levelsByType)),
    processes: list.map(({ id, name, level, abnormal }) => ({ id, name, level, abnormal })),
    todayAlarmCount: mockAlarms().filter((a) => a.factoryId === 5).length,
  }
}

/** 사업장 펼치기 { 사업장 id: { siteId, factories } } (지금 상황) */
export function mockSiteTrees() {
  const trees = {
    1: { siteId: 1, factories: [...ULSAN_BEFORE_5, plant5(), ULSAN_ENGINE] },
    2: { siteId: 2, factories: ASAN },
    3: { siteId: 3, factories: JEONJU },
  }
  // 공장 관리에서 추가한 사업장 · 공장 · 공정 붙이기 (addedScope.js)
  for (const site of ADDED.sites) trees[site.id] = { siteId: site.id, factories: [] }
  for (const tree of Object.values(trees)) {
    tree.factories = [...tree.factories, ...addedFactoriesOf(tree.siteId)].map((f) => ({
      ...f,
      processes: [...f.processes, ...extraProcessesOf(f.id)],
    }))
  }
  return trees
}

/* ───────── 사업장 목록 (GET /sites) ───────── */

const SITE_INFO = [
  { id: 1, code: 'USN', name: '울산공장', address: '울산광역시 북구 염포로 700', lat: 35.5438, lng: 129.3697, processCount: 22 },
  { id: 2, code: 'ASN', name: '아산공장', address: '충청남도 아산시 인주면 현대로 1077', lat: 36.9237, lng: 126.9148, processCount: 6 },
  { id: 3, code: 'JJU', name: '전주공장', address: '전북특별자치도 완주군 봉동읍 완주산단5로 163', lat: 35.9198, lng: 127.1398, processCount: 7 },
]

/** 사업장 목록 (지금 상황): 단계·센서 개수는 공장 합, 울산 오늘 알림은 알림 목록에서 셈 (아산 1건은 고정) */
export function mockSites() {
  const trees = mockSiteTrees()
  const ulsanAlarms = mockAlarms().filter((a) => a.siteId === 1).length
  // 공장 관리에서 추가한 사업장은 주소 · 위경도 없이 목록 끝에 (addedScope.js)
  return [...SITE_INFO, ...ADDED.sites].map((info) => {
    const factories = trees[info.id].factories
    const sensorLevels = totalOf(sumByType(factories.map((f) => f.levelsByType)))
    return {
      ...info,
      level: maxLevel(factories.map((f) => f.level)),
      factoryCount: factories.length,
      sensorCount: sensorLevels[NORMAL] + sensorLevels[LEVEL.WARN] + sensorLevels[LEVEL.ALARM],
      sensorLevels,
      processCount: factories.reduce((n, f) => n + f.processes.length, 0), // 공정 수 = 공장들의 공정 합 (추가한 공정도 셈)
      todayAlarmCount: { 1: ulsanAlarms, 2: 1, 3: 0 }[info.id] ?? 0,
    }
  })
}

/* ───────── 알림 (GET /alarms, GET /alarms/hourly) ───────── */

/** 오늘 날짜의 시각 문자열 (예: '16:38:20' → '2026-11-16T16:38:20+09:00') */
export function todayAt(time) {
  const d = new Date()
  const ymd = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return `${ymd}T${time}+09:00`
}

/** 알림 한 건 만들기 (siteId·factoryId·processId 는 범위로 거르는 값. 백엔드 AlarmViews 응답에도 있음) */
const alarm = (id, factoryId, status, title, location, sensorTag, sensorType, value, unit, threshold, time) => ({
  // 5공장 알림은 모두 도장 공정(processId 12)
  siteId: 1, factoryId, processId: factoryId === 5 ? 12 : null, id, level: LEVEL.WARN, status, title, location,
  equipmentId: id - 1000, sensorId: id - 900, sensorTag, sensorType, value, unit, threshold, raisedAt: todayAt(time),
  ackedBy: null, ackedAt: null, closedAt: null, checklist: null, assignee: null,
  fanAuto: null, // 가스 알림 때 배기팬 자동 제어 결과 (fanAuto.js) — 그 밖의 알림은 null
  // 설비 이름 (백엔드 AlarmViews unitName) — 위치 마지막 칸에서 부품 이름을 뺀 것
  unitName: location.split(' · ').at(-1).replace(/ (배기팬|버너실|순환펌프)$/, ''),
})

/** 이미 끝난 오늘 알림 (상황과 상관없음) */
const PAST_ALARMS = [
  alarm(1040, 5, 'CLOSED', '온도 주의 해제', '5공장 · 도장 공정 · 전착 건조로', 'TE-13', 'TEMP', 162, '℃', 160, '14:05:41'),
  alarm(1038, 2, 'CLOSED', '온도 주의 해제', '2공장 · 차체 공정 · 용접 라인', 'TE-204', 'TEMP', 38, '℃', 35, '13:12:05'),
  alarm(1035, 5, 'CLOSED', 'CO 주의 해제', '5공장 · 도장 공정 · 중도 건조로 버너실', 'CO-12', 'CO', 21, 'ppm', 20, '11:32:10'),
  alarm(1031, 1, 'CLOSED', 'CO 주의 해제', '1공장 · 도장 공정 · 버너실', 'CO-112', 'CO', 12, 'ppm', 10, '10:47:31'),
  alarm(1029, 6, 'CLOSED', '전류 주의 해제', '엔진·변속기 공장 · 가공 라인', 'CT-631', 'CURRENT', 9.8, 'A', 9.5, '09:20:14'),
  alarm(1027, 5, 'CLOSED', '전류 주의 해제', '5공장 · 도장 공정 · 전처리 순환펌프', 'CT-11', 'CURRENT', 12.4, 'A', 12, '08:47:55'),
  alarm(1019, 2, 'CLOSED', '온도 주의 해제', '2공장 · 프레스 공정 · 유압 펌프', 'TE-118', 'TEMP', 61, '℃', 60, '02:13:40'),
]

/**
 * 상도 건조로 전류 주의 알림 (예시 상황) — 없으면 null
 * - 발생 중(OPEN) → 확인함(ACKED) → 조치 중(ACTING, 체크리스트 진행) → 조치 완료(CLOSED, '전류 주의 해제')
 * - value 는 넘었을 때의 값(22.4 A) 그대로 (다른 끝난 알림처럼)
 */
function topOvenAlarm() {
  const a = scenario.alarm
  if (!a) return null
  // 가스 주의(kind GAS, 2026-11-19 추가)면 버너실 CO-13 24 ppm, 아니면 그동안의 배기팬 전류 CT-17 22.4 A
  const gas = a.kind === 'GAS'
  const name = gas ? 'CO 주의' : '전류 주의'
  const title = a.status === 'CLOSED' ? `${name} 해제` : `${name} 기준 초과`
  const row = gas
    ? { ...alarm(1042, 5, a.status, title, '5공장 · 도장 공정 · 상도 건조로 버너실', 'CO-13', 'CO', 24, 'ppm', 20, a.raisedAt), equipmentId: 31 }
    : { ...alarm(1042, 5, a.status, title, '5공장 · 도장 공정 · 상도 건조로 배기팬', 'CT-17', 'CURRENT', 22.4, 'A', 21.6, a.raisedAt), equipmentId: 31 }
  const c = scenario.checklist
  return {
    ...row,
    // 가스 알림이면 배기팬 자동 제어 결과 (서버가 알림을 만들 때 함께 정함, fanAuto.js)
    fanAuto: gas ? fanAutoOf({ equipmentId: 31, level: LEVEL.WARN, at: row.raisedAt }) : null,
    ackedBy: a.ackedBy,
    ackedAt: a.ackedAt && todayAt(a.ackedAt),
    closedAt: a.closedAt && todayAt(a.closedAt),
    // 백엔드 AlarmViews 처럼 체크리스트 진행과 담당자를 같이 줌 (알림 줄 '조치 중 · 김도현 (2/5)')
    checklist: c ? { id: c.id, status: c.status, doneCount: c.doneTimes.length, totalCount: c.items.length } : null,
    assignee: c ? workerName(c.assigneeId) : null,
  }
}

/**
 * 경보·화재 알림 (예시 상황, scenarioEmergency.js)
 * - 경보: 16:47:36 전류 경보(CT-17 27.5 A) · 16:50:12 온도 주의(TE-15 164 ℃) · 16:52:08 CO 경보(CO-13 34 ppm)
 * - 화재: 16:58:04 화재 감지(열화상 최고 486 ℃) — 레벨 FIRE
 * - 경보 종료·화재 해제 뒤에는 모두 '조치 완료'(CLOSED), 제목은 '… 해제'
 */
function emergencyAlarms() {
  const inc = scenario.incident
  if (!inc) return []
  const closed = inc.status === 'CLOSED'
  const status = closed ? 'CLOSED' : 'OPEN'
  const loc = '5공장 · 도장 공정 · 상도 건조로'
  const row = (id, level, title, place, tag, type, value, unit, threshold, time) => ({
    ...alarm(id, 5, status, closed ? title.replace('기준 초과', '해제').replace('감지', '해제') : title, place, tag, type, value, unit, threshold, time),
    level,
    equipmentId: 31, // 상도 건조로
    closedAt: closed ? todayAt(inc.closedAt) : null,
    // 가스(CO) 경보면 배기팬 자동 제어 결과 (100%), 그 밖에는 null
    fanAuto: type === 'CO' ? fanAutoOf({ equipmentId: 31, level, at: todayAt(time) }) : null,
  })
  const list = [
    row(1051, LEVEL.ALARM, 'CO 경보 기준 초과', `${loc} 버너실`, 'CO-13', 'CO', 34, 'ppm', 30, designAt('16:52:08')), // 설계안 시각 → 지금 상황 기준 (scenario.js anchorAt)
    row(1049, LEVEL.WARN, '온도 주의 기준 초과', loc, 'TE-15', 'TEMP', 164, '℃', 160, designAt('16:50:12')),
    row(1050, LEVEL.ALARM, '전류 경보 기준 초과', `${loc} 배기팬`, 'CT-17', 'CURRENT', 27.5, 'A', 27, designAt('16:47:36')),
  ]
  const f = scenario.fire
  if (f) {
    const fireClosed = f.status === 'CLEARED'
    list.unshift({
      ...alarm(1052, 5, fireClosed ? 'CLOSED' : 'OPEN', fireClosed ? '화재 경보 해제' : '화재 감지', `${loc} 버너실`, 'IR-01', 'TEMP', 486, '℃', 150, f.detectedAt),
      level: LEVEL.FIRE,
      closedAt: fireClosed ? todayAt(f.clearedAt) : null,
    })
  }
  return list
}

/** '주의 1건 추가'로 넣은 알림 (중도 건조로 버너실 CO-12 22 ppm 가스 주의, scenario.extra) — 없으면 null */
function extraAlarm() {
  const e = scenario.extra
  if (!e) return null
  return {
    ...alarm(e.id, 5, e.status, 'CO 주의 기준 초과', '5공장 · 도장 공정 · 중도 건조로 버너실', 'CO-12', 'CO', 22, 'ppm', 20, e.raisedAt),
    equipmentId: 29, // 중도 건조로
    hazard: 'GAS',
    ackedBy: e.ackedBy,
    ackedAt: e.ackedAt && todayAt(e.ackedAt),
  }
}

/** 오늘 알림 (최근 순, 지금 상황) */
export function mockAlarms() {
  const top = topOvenAlarm()
  const extra = extraAlarm()
  return [...(extra ? [extra] : []), ...emergencyAlarms(), ...(top ? [top] : []), ...PAST_ALARMS]
}

/** 진행 중인 체크리스트가 있는지 (상단 바 숫자) */
export const hasActiveChecklist = () => !!activeChecklist()
