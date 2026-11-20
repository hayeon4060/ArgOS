/**
 * 설비 상세 예시 데이터 (5공장 도장 공정 설비 11개, id 24~34)
 * - 응답 모양: API 명세서 B4 (GET /equipment/{id}, /equipment/{id}/events, /thermal/{cameraId}/latest)
 * - 설비·센서·지금 값은 공정 배치도 예시 데이터(processData.js)를 그대로 씀 → 숫자가 다른 화면과 같음
 * - 상도 건조로는 예시 상황(scenario.js)에 따라 값·단계·이벤트·열화상·체크리스트가 바뀜
 *   (주의: CT-17 22.4 A, TE-15 146 ℃, CO-13 9 ppm / 정상: 18.1 A, 142 ℃, 6 ppm)
 * - 게이지 눈금 끝(max)·평소 값(usual)은 화면설계안 js/v-dash.js EQD 값
 */
import { addedLayout } from './addedScope'
import { EQUIPMENT, equipmentLevel, sensorsNow } from './processData'
import { SCENARIO_TIME, todayIso } from './equipmentSeries'
import { activeChecklist, designAt, scenario, workerName } from './scenario'
import { LEVEL } from '@/types/codes'
import irAlarm from '@/assets/images/thermal/ir_alarm.jpg'
import irFire from '@/assets/images/thermal/ir_fire.jpg'
import irNormal from '@/assets/images/thermal/ir_normal.jpg'
import irWarn from '@/assets/images/thermal/ir_warn.jpg'

/** 센서별 게이지 눈금 끝(max)과 평소 값(usual) — 설계안 EQD [눈금 끝, 평소값] */
export const GAUGE = {
  'CT-11': [20, 9.5],
  'TE-11': [100, 51],
  'CT-12': [18, 7.2],
  'TE-12': [50, 28],
  'CT-13': [36, 16],
  'TE-13': [240, 150],
  'CO-11': [50, 6],
  'CT-14': [30, 14],
  'CT-15': [36, 17],
  'TE-14': [240, 147],
  'CO-12': [50, 8],
  'CT-16': [30, 15],
  'CT-17': [36, 18],
  'TE-15': [240, 142],
  'CO-13': [50, 6],
  'CT-21': [25, 11.5],
  'CT-22': [12, 4.5],
  'TE-21': [1000, 740],
}

const TYPE_LABEL = { CURRENT: '전류', TEMP: '온도', CO: 'CO' }

/** 상도 건조로 (예시 상황이 걸린 설비) */
export const TOP_OVEN_ID = 31

/**
 * 공장 관리에서 도장 공정에 추가한 설비의 상세 (2026-11-20) — 센서 · 열화상 · 체크리스트가 아직 없는 빈 설비
 * (없는 id 면 null → 404)
 */
function mockAddedEquipment(equipmentId) {
  const e = addedLayout(12).equipment.find((x) => x.id === Number(equipmentId))
  if (!e) return null
  return {
    id: e.id, code: e.code, name: e.name, type: e.type, processId: 12, seq: null,
    path: ['울산공장', '5공장', '도장 공정'],
    level: LEVEL.NORMAL, runState: 'RUNNING', sensors: [], cameraId: null, activeChecklist: null,
  }
}

/** 설비 상세: GET /equipment/{id} (지금 상황). 없는 설비면 null */
export function mockEquipment(equipmentId) {
  const e = EQUIPMENT.find((x) => x.id === Number(equipmentId))
  if (!e) return mockAddedEquipment(equipmentId)
  const sensors = sensorsNow()
  const c = e.id === TOP_OVEN_ID ? activeChecklist() : null
  return {
    id: e.id,
    code: e.code,
    name: e.name,
    type: e.type,
    processId: 12,
    seq: e.seq,
    path: ['울산공장', '5공장', '도장 공정'],
    level: equipmentLevel(e.id, sensors),
    runState: 'RUNNING',
    sensors: sensors
      .filter((s) => s.equipmentId === e.id)
      .map((s) => ({
        id: s.id,
        tag: s.tag,
        type: s.type,
        label: `${e.name} ${TYPE_LABEL[s.type]}`,
        unit: s.unit,
        value: s.value,
        level: s.level,
        min: 0,
        max: GAUGE[s.tag][0],
        usual: GAUGE[s.tag][1], // 명세서에 없음 (게이지 아래 '평소 18.0 A')
        warn: s.warn,
        alarm: s.alarm,
        season: 'WINTER',
      })),
    cameraId: 301, // 도장 공정 열화상 카메라 IR-01 (공정마다 1대)
    // 진행 중 체크리스트 (상도 건조로만, 상단 바 체크리스트와 같은 건)
    activeChecklist: c
      ? { id: c.id, status: c.status, doneCount: c.doneTimes.length, totalCount: c.items.length, assignee: workerName(c.assigneeId) }
      : null,
  }
}

/* ───────── 이벤트 이력: GET /equipment/{id}/events?date=today (최근이 위) ─────────
 * type: ALARM(알람) | ACTION(조치) | THRESHOLD(기준값 조정) | RECORD(가동·점검 기록, 명세서에 없음)
 */
const ev = (time, type, text, detail, extra = {}) => ({ at: todayIso(time), type, text, detail, ...extra })

/** 상도 건조로: 오늘 기본 기록 + 예시 상황의 알림 대응 단계 */
function topOvenEvents() {
  const list = [
    // 기준값 관리 변경 이력(thresholdData.js)의 오늘 07:40 줄과 같은 내용
    ev('07:40:00', 'THRESHOLD', '전류 주의 기준 20.7 → 21.6 A', '오승민 · 베어링 교체 완료, 겨울 기준으로 되돌림', { by: '오승민' }),
    ev('08:30:00', 'RECORD', '가동 시작', '상도 건조로 승온 완료 142 ℃'),
    ev('09:02:40', 'RECORD', '일일 점검 완료', '배기팬 벨트·베어링 점검 · 김도현', { by: '김도현' }),
  ]
  const a = scenario.alarm
  if (a) {
    const id = { alarmId: a.id }
    list.push(ev(designAt('16:30:12'), 'ALARM', '전류 상승 시작', 'CT-17 18.0 → 19.2 A', { level: LEVEL.NORMAL }))
    list.push(ev(a.raisedAt, 'ALARM', '전류 주의 기준 초과', 'CT-17 22.4 A (기준 21.6 A)', { level: LEVEL.WARN, ...id }))
    if (a.ackedAt) list.push(ev(a.ackedAt, 'ACTION', '주의 확인', `${a.ackedBy} · 2초 길게 눌러 확인`, { by: a.ackedBy, ...id }))
    const c = scenario.checklist
    if (c) {
      // (경보 상황에서 만든 체크리스트는 시각만 다름 — 같은 방식으로 보여 줌)
      const name = workerName(c.assigneeId)
      list.push(ev(c.sentAt, 'ACTION', '체크리스트 전송', `도장 공정 담당자 앱 · ${c.items.length}개 항목`, id))
      c.reassigned.forEach((r) => list.push(ev(r.at, 'ACTION', '담당자 변경', `${workerName(r.from)} → ${workerName(r.to)} · ${r.reason}`, id)))
      const done = c.doneTimes.length
      if (c.reportedAt) list.push(ev(c.reportedAt, 'ACTION', `체크리스트 ${done}개 항목 완료`, `${name} · 확인 대기`, id))
      else if (done) list.push(ev(c.doneTimes.at(-1), 'ACTION', '조치 중', `${name} · 체크리스트 ${done}/${c.items.length} 진행`, { status: 'ACTING', ...id }))
      if (c.confirmedAt && !scenario.incident) list.push(ev(c.confirmedAt, 'ACTION', '조치 확인 · 주의 해제', '공장 담당자 확인', id))
    }
  }
  list.push(...emergencyEvents())
  return list.sort((x, y) => y.at.localeCompare(x.at))
}

/** 경보·셧다운·화재 기록 (설계안 eventLog() 'dng' · 'fire') */
function emergencyEvents() {
  const inc = scenario.incident
  if (!inc) return []
  const id = { alarmId: inc.id }
  const list = [
    // 설계안 시각 → 지금 상황 기준 (scenario.js anchorAt)
    ev(designAt('16:47:36'), 'ALARM', '전류 경보 기준 초과', 'CT-17 27.5 A (기준 27.0 A)', { level: LEVEL.ALARM, ...id }),
    ev(designAt('16:50:12'), 'ALARM', '온도 주의 기준 초과', 'TE-15 164 ℃ (기준 160 ℃)', { level: LEVEL.WARN, ...id }),
    ev(designAt('16:52:08'), 'ALARM', 'CO 경보 기준 초과', 'CO-13 34 ppm (기준 30 ppm)', { level: LEVEL.ALARM, ...id }),
  ]
  const sd = scenario.shutdown
  if (sd && !sd.auto) {
    list.push(ev(sd.startedAt, 'ACTION', '셧다운 명령', `설비 ${sd.equipmentIds.length}대 · 공장 담당자`, id))
    if (sd.steps[2]) list.push(ev(sd.steps[2], 'ACTION', '상도 건조로 정지됨', '버너 연료 차단 → 컨베이어 정지 → 배기팬 정지', id))
  }
  const f = scenario.fire
  if (f) {
    list.push(ev(f.detectedAt, 'ALARM', '화재 감지', '열화상 최고 486 ℃', { level: LEVEL.FIRE, ...id }))
    list.push(ev(designAt('16:58:05'), 'ACTION', '공장 내 경고 방송 송출', '작업자 대피 안내', id))
    list.push(ev(designAt('16:58:06'), 'ACTION', '상도 건조로 자동 셧다운', '버너 연료 차단', id))
    if (f.clearedAt) list.push(ev(f.clearedAt, 'ACTION', '화재 경보 해제', `${f.clearedBy} · 비밀번호 확인`, id))
  }
  if (inc.status === 'CLOSED' && !f) list.push(ev(inc.closedAt, 'ACTION', '경보 종료', inc.result || '설비 정지 후 기준 아래로 복귀', id))
  return list
}

/** 이벤트 이력 (그 밖의 설비는 점검·가동 기록만, 센서 없는 설비는 기록 없음) */
export function mockEquipmentEvents(equipmentId) {
  const id = Number(equipmentId)
  if (id === TOP_OVEN_ID) return topOvenEvents()
  if (!sensorsNow().some((s) => s.equipmentId === id)) return []
  const e = EQUIPMENT.find((x) => x.id === id)
  return [
    ev('09:02:40', 'RECORD', '일일 점검 완료', '환기·배기설비 점검일지 등록'),
    ev('08:30:00', 'RECORD', '가동 시작', `${e?.name ?? ''} 정상 가동`),
  ]
}

/**
 * 열화상 최신 장면: GET /thermal/{cameraId}/latest?equipmentId=
 * - 그림: 설계안 assets 의 예시 열화상 (주의 = ir_warn.jpg, 정상 = ir_normal.jpg)
 * - 상도 건조로 구역 온도: 주의 78.4 / 22.8 ℃, 정상 52.0 / 21.4 ℃ (설계안 vEquip() IR 값)
 * - zone: 이 설비 구역 (구역이 정해진 설비만 최고·최저 온도가 있음. 명세서에 없음)
 */
/** 상황별 상도 건조로 구역 열화상 (설계안 vEquip() IR: 그림, 최고, 최저, 평균) */
export const THERMAL_BY_STATE = {
  [LEVEL.NORMAL]: { image: irNormal, max: 52.0, min: 21.4, avg: 33.8, point: { x: 0.62, y: 0.13 }, scale: { min: 20, max: 120 } },
  [LEVEL.WARN]: { image: irWarn, max: 78.4, min: 22.8, avg: 41.3, point: { x: 0.62, y: 0.13 }, scale: { min: 20, max: 120 } },
  [LEVEL.ALARM]: { image: irAlarm, max: 96.2, min: 22.8, avg: 48.6, point: { x: 0.62, y: 0.13 }, scale: { min: 20, max: 120 } },
  [LEVEL.FIRE]: { image: irFire, max: 486, min: 24.0, avg: 131, point: { x: 0.84, y: 0.62 }, scale: { min: 20, max: 500 } },
}

/**
 * VMS 실시간 영상 주소 예시 (백엔드가 알려 준 실제 모양: GET /thermal/331/latest → streamUrl)
 * - VMS 는 3단계(경보 · 화재)일 때만 영상을 줌 → 예시 데이터도 3단계일 때만 채움
 * - mock 모드에서는 보통 이 주소에 닿지 않음 → 화면이 7초 뒤 사진으로 되돌아가는 모습을 볼 수 있음
 */
export const SAMPLE_STREAM_URL = 'http://192.168.0.16:9000/stream/IR-301'
const isStage3 = (level) => level === LEVEL.ALARM || level === LEVEL.FIRE

export function thermalOf(cameraId, equipmentId) {
  const top = Number(equipmentId) === TOP_OVEN_ID
  // 상도 건조로 구역만 상황에 따라 바뀜 (다른 설비는 공정 전체 정상 그림, 최고·최저 없음)
  const level = top ? scenario.state : LEVEL.NORMAL
  const t = THERMAL_BY_STATE[level]
  return {
    cameraId,
    tag: 'IR-01',
    level,
    imageUrl: t.image,
    streamUrl: isStage3(level) ? SAMPLE_STREAM_URL : null, // 3단계만 영상
    capturedAt: todayIso(designAt(SCENARIO_TIME)),
    max: top ? t.max : null,
    min: top ? t.min : null,
    avg: top ? t.avg : null,
    maxPoint: top ? t.point : null,
    scale: t.scale, // 색 눈금 범위 (명세서에 없음)
    zone: top ? { name: '배기팬 모터부', desc: '상도 건조로 외벽 · 배기팬 · 버너실' } : null,
  }
}
