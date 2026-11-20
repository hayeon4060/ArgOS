/**
 * 공정 3D 배치도 예시 데이터 (5공장 도장 공정, processId 12)
 * - 응답 모양: API 명세서 B3 (GET /processes/{id}/layout, /status, GET /inspections/today)
 * - 값: 화면설계안 js/v-dash.js EQD(설비·센서), TODO(오늘 점검)
 * - 상도 건조로 센서 3개(CT-17·TE-15·CO-13)의 지금 값은 예시 상황(scenario.js)에서 가져옴
 *   예) 주의: CT-17 22.4 A 주의 / 정상: 18.1 A → 공장·사업장 화면 숫자도 같이 바뀜
 * - 센서 19개 = 전류 9 + 온도 6 + CO 4 (CO-14 는 예비 센서라 그림에 위치 없음)
 */
import { addedLayout } from './addedScope'
import { LAYOUT, editsOf } from './layoutData'
import { FAN_RATED_RPM, autoRpmOf, fanAutoMode } from './fanAuto'
import { isGasWarn, scenario, topOvenValue } from './scenario'
import { seasonThreshold } from './thresholdRules'
import { LEVEL } from '@/types/codes'

const { NORMAL, WARN, ALARM, FIRE } = LEVEL

/**
 * 설비 (seq: 공정 흐름 순서 번호, 그림과 아래 설비 줄에 표시. 명세서에 없는 값)
 * - 설비 상세 예시 데이터(equipmentData.js)도 이 목록을 그대로 씀 → 두 화면 숫자가 어긋나지 않음
 * - type: DB equipment.equipment_type 코드. 지금 DB 시드 · 체크리스트 양식에 있는 코드(OVEN · TANK …)만 쓰고,
 *   코드가 정해지지 않은 설비(실링 · 부스 · 공조기 · 도료 혼합실 · 소각설비)는 null (backend-notes 15장 질문)
 * - code: 실제 서버(중앙 시드 공정 12)에 있는 설비는 그 코드 (PT-TANK · ED-TANK · ED-OVEN · PR-OVEN · TC-OVEN, 2026-11-20)
 *   실제 시드에 없는 설비(실링 · 부스 · 공조기 · 도료 혼합실 · 소각설비)는 화면설계안 그림 때문에 'PNT-' 코드로 남김
 *   (실제 서버의 배기팬 · 버너실 · 펌프 · 열화상 카메라는 따로 설비지만, 예시는 건조로 · 탱크 한 대로 묶어 둠)
 */
export const EQUIPMENT = [
  { id: 24, code: 'PT-TANK', name: '전처리', type: 'TANK', seq: 1, manager: '이재호' },
  { id: 25, code: 'ED-TANK', name: '전착 탱크', type: 'TANK', seq: 2, manager: '이재호' },
  { id: 26, code: 'ED-OVEN', name: '전착 건조로', type: 'OVEN', seq: 3, manager: '박성훈' },
  { id: 27, code: 'PNT-SEALING', name: '실링', type: null, seq: 4, manager: '박성훈' },
  { id: 28, code: 'PNT-PRIMER-BOOTH', name: '중도 부스', type: null, seq: 5, manager: '박성훈' },
  { id: 29, code: 'PR-OVEN', name: '중도 건조로', type: 'OVEN', seq: 6, manager: '박성훈' },
  { id: 30, code: 'PNT-TOP-BOOTH', name: '상도 부스', type: null, seq: 7, manager: '김도현' },
  { id: 31, code: 'TC-OVEN', name: '상도 건조로', type: 'OVEN', seq: 8, manager: '김도현' },
  { id: 32, code: 'PNT-AHU', name: '공조기 (AHU)', type: null, seq: null, manager: '이재호' },
  { id: 33, code: 'PNT-PAINT-MIX', name: '도료 혼합실', type: null, seq: null, manager: '이재호' },
  { id: 34, code: 'PNT-INCINERATOR', name: '소각설비', type: null, seq: null, manager: '이재호' },
]

/** 센서: [id, tag, 종류, 단위, 설비 id, 평상시 값, 주의 기준, 경보 기준] — id 는 전류 1xx · 온도 2xx · CO 3xx */
const SENSOR_ROWS = [
  [111, 'CT-11', 'CURRENT', 'A', 24, 9.8, 12, 15],
  [211, 'TE-11', 'TEMP', '℃', 24, 52, 60, 70],
  [112, 'CT-12', 'CURRENT', 'A', 25, 7.4, 10, 13],
  [212, 'TE-12', 'TEMP', '℃', 25, 28, 32, 36],
  [113, 'CT-13', 'CURRENT', 'A', 26, 16.2, 21.6, 27],
  [213, 'TE-13', 'TEMP', '℃', 26, 151, 160, 180],
  [311, 'CO-11', 'CO', 'ppm', 26, 7, 20, 30],
  [114, 'CT-14', 'CURRENT', 'A', 28, 14.1, 18, 22],
  [115, 'CT-15', 'CURRENT', 'A', 29, 17.3, 21.6, 27],
  [214, 'TE-14', 'TEMP', '℃', 29, 148, 160, 180],
  [312, 'CO-12', 'CO', 'ppm', 29, 12, 20, 30],
  [116, 'CT-16', 'CURRENT', 'A', 30, 15.2, 18, 22],
  [117, 'CT-17', 'CURRENT', 'A', 31, 18.1, 21.6, 27], // 상도 건조로: 지금 값은 예시 상황에서
  [215, 'TE-15', 'TEMP', '℃', 31, 142, 160, 180],
  [313, 'CO-13', 'CO', 'ppm', 31, 6, 20, 30],
  [121, 'CT-21', 'CURRENT', 'A', 32, 11.8, 15, 18],
  [122, 'CT-22', 'CURRENT', 'A', 33, 4.6, 6, 8],
  [221, 'TE-21', 'TEMP', '℃', 34, 742, 820, 880],
  [314, 'CO-14', 'CO', 'ppm', null, 5, 20, 30], // 예비 센서 (설비에 아직 안 붙음)
]

/** 값 → 단계 (경보 기준 이상 경보, 주의 기준 이상 주의) */
export const levelOf = (value, warn, alarm) => (value >= alarm ? ALARM : value >= warn ? WARN : NORMAL)

/** 센서 정의 (기본 겨울 기준 포함) — 기준값 관리 예시 데이터가 씀 */
export const SENSOR_DEFS = SENSOR_ROWS.map(([id, tag, type, unit, equipmentId, usualValue, warn, alarm]) => ({
  id, tag, type, unit, equipmentId, usualValue, warn, alarm,
}))

/**
 * 지금 센서 목록 (상도 건조로 3개는 예시 상황 값, 나머지는 평상시 값)
 * - 기준은 지금 계절 기준 (기준값 관리에서 저장했으면 그 값) → 단계·게이지·기준선이 같이 바뀜
 */
export function sensorsNow() {
  return SENSOR_DEFS.map((d) => {
    const value = topOvenValue(d.id) ?? d.usualValue
    const { warn, alarm } = seasonThreshold(d)
    const { id, tag, type, unit, equipmentId } = d
    return { id, tag, type, unit, equipmentId, value, warn, alarm, level: levelOf(value, warn, alarm) }
  })
}

/** 단계 높은 쪽 (정상 < 주의 < 경보 < 화재) */
const ORDER = [NORMAL, WARN, ALARM, LEVEL.FIRE]
export const maxLevel = (levels) => levels.reduce((a, b) => (ORDER.indexOf(b) > ORDER.indexOf(a) ? b : a), NORMAL)

/** 설비 단계 = 붙은 센서 중 가장 높은 단계 (화재 중에는 상도 건조로가 '화재') */
export function equipmentLevel(equipmentId, sensors = sensorsNow()) {
  if (equipmentId === 31 && scenario.state === FIRE) return FIRE
  return maxLevel(sensors.filter((s) => s.equipmentId === equipmentId).map((s) => s.level))
}

/**
 * 설비 운전 상태 (셧다운 명령을 받은 설비만 바뀜)
 * - RUNNING 가동 / STOPPING 정지 중(셧다운 단계 진행) / STOPPED 정지됨
 */
export function runStateOf(equipmentId) {
  const sd = scenario.shutdown
  if (!sd || !sd.equipmentIds.includes(equipmentId)) return 'RUNNING'
  return sd.steps.every(Boolean) ? 'STOPPED' : 'STOPPING'
}

/**
 * 배기팬이 있는 설비 → 팬 상태 (상도 건조로는 주의 이상이면 회전수를 올려 둔 상태, 정지되면 꺼짐)
 * - 가스(CO) 알림 + 배기팬 자동이면 자동 회전수 (주의 75% 1,350 rpm · 경보 100% 1,800 rpm, fanAuto.js)
 *   수동이면 그동안처럼 1,240 rpm (사람이 올려 둔 값)
 * - 평소(알림 없음)는 1,200 rpm (40 Hz) — 알림이 끝나면 서버가 이 값으로 되돌림
 */
function fansNow(topLevel) {
  const fan = { power: 'ON', rpm: FAN_RATED_RPM, targetRpm: FAN_RATED_RPM }
  const fanOf = (id, normalFan) =>
    runStateOf(id) === 'STOPPED' || (id === 31 && scenario.shut) ? { power: 'OFF', rpm: 0, targetRpm: 0 } : normalFan
  const on = (rpm) => ({ power: 'ON', rpm, targetRpm: rpm })
  // 상도 건조로 가스 알림: 가스 주의(1042 GAS) 또는 CO 경보(1051, 경보 상황)
  const gasLevel = isGasWarn() ? WARN : scenario.state === ALARM && scenario.incident?.status === 'OPEN' ? ALARM : null
  const topFan = topLevel === NORMAL ? fan : gasLevel && fanAutoMode(31) ? on(autoRpmOf(gasLevel)) : on(1240)
  return {
    26: fanOf(26, fan),
    28: fanOf(28, fan),
    29: fanOf(29, fan),
    30: fanOf(30, fan),
    31: fanOf(31, topFan),
  }
}

/** 오늘 설비 점검 (설계안 '오늘 설비 점검' 5건, 상황과 상관없음) */
const INSPECTIONS = {
  doneCount: 3,
  totalCount: 5,
  items: [
    { scheduleId: 640, time: '08:30', equipmentName: '상도 건조로', title: '배기팬 벨트·베어링 점검', assignee: '김도현', status: 'DONE' },
    { scheduleId: 641, time: '09:00', equipmentName: '전착 건조로', title: '버너실 CO 감지기 작동 확인', assignee: '박성훈', status: 'DONE' },
    { scheduleId: 642, time: '10:30', equipmentName: '전처리', title: '순환펌프 누수·진동 점검', assignee: '이재호', status: 'DONE' },
    { scheduleId: 643, time: '14:00', equipmentName: '소각설비', title: '연소실 온도 기록지 확인', assignee: '이재호', status: 'DELAYED' },
    { scheduleId: 644, time: '17:00', equipmentName: '중도 건조로', title: '배기 덕트 댐퍼 개도 확인', assignee: '박성훈', status: 'PLANNED' },
  ],
}


/**
 * 공정 예시 데이터 (부를 때마다 지금 상황으로 만듦)
 * @returns {{ layout, status, inspections } | null}
 */
export function mockProcess(processId) {
  if (Number(processId) !== 12) return null
  const sensors = sensorsNow()
  const levelOfEq = (id) => equipmentLevel(id, sensors)
  const fans = fansNow(levelOfEq(31))
  // 공장 관리 설비 추가 · 삭제 (2026-11-20): 지운 원래 설비는 빼고, 추가한 설비(그림 이름 shape · 위치 pos)를 뒤에 붙임
  const seed = EQUIPMENT.filter((e) => !LAYOUT.removed.includes(e.id))
  const extra = structuredClone(addedLayout(12).equipment)
  return {
    // GET /processes/{processId}/layout
    layout: {
      processId: 12,
      name: '도장 공정',
      version: LAYOUT.version,
      floorplan: null,
      // 백엔드 DashboardService.processLayout 과 같은 모양: type(equipment_type) · pos{x,y,z}(pos_x · pos_y · pos_z) · active(is_active)
      // 위치는 DB 시드처럼 처음엔 비어 있음(그림에 그려진 자리). 공장 관리에서 저장한 값을 덮어씀 (layoutData.js)
      equipment: [
        ...seed.map(({ manager, ...e }) => ({
          ...e,
          pos: { x: null, y: null, z: null },
          active: true,
          managers: [{ id: 0, name: manager, primary: true }],
          // 배기팬 자동(true)/수동(false), 배기팬이 없는 설비는 null (2026-11-19 추가, DB equipment.fan_auto)
          fanAuto: fanAutoMode(e.id),
          ...editsOf(e.id),
        })),
        ...extra,
      ],
      sensors: sensors.map(({ id, tag, type, unit, equipmentId }) => ({ id, tag, type, unit, equipmentId, active: true })),
    },
    // GET /processes/{processId}/status
    status: {
      processId: 12,
      level: scenario.state === FIRE ? FIRE : maxLevel(sensors.map((s) => s.level)),
      equipment: [
        ...seed.map((e) => ({ id: e.id, level: levelOfEq(e.id), runState: runStateOf(e.id), fan: fans[e.id] ?? null })),
        ...extra.map((e) => ({ id: e.id, level: LEVEL.NORMAL, runState: 'RUNNING', fan: null })), // 추가한 설비는 센서가 없어 늘 정상
      ],
      sensors: sensors.map(({ id, tag, value, level, warn, alarm }) => ({ id, tag, value, level, warn, alarm })),
    },
    // GET /inspections/today?processId=12
    inspections: INSPECTIONS,
  }
}
