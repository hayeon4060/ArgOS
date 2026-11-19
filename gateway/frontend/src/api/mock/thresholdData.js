/**
 * 설비 기준값 관리 예시 응답 (명세서 F1, 백엔드 ThresholdController · ThresholdAdminService 필드 기준)
 * - GET  /equipment/tree?factoryId=          : 공정 > 설비 목록
 * - GET  /equipment/{id}/thresholds          : 센서별 계절 기준
 * - POST /thresholds/simulate                : 최근 7일에 적용했을 때 알림 수
 * - PUT  /equipment/{id}/thresholds          : 저장 (지금 계절이면 바로 반영)
 * - GET  /thresholds/history                 : 변경 이력
 * - GET  /sensors/series (7일, 30분)          : 변경 미리보기 그래프
 * - 값: 화면설계안 js/v-pages2.js vThreshold() (계절, 이력 THH, 7일 그래프 series())
 */
import { todayAt } from './data'
import { GAUGE } from './equipmentData'
import { EQUIPMENT, SENSOR_DEFS, equipmentLevel, sensorsNow } from './processData'
import { emitLive } from '../live'
import { scenario, scenarioNow } from './scenario'
import { ACTIVE_SEASON, HOLD_SEC, SEASONS, SEASON_START, seasonThreshold, storeThreshold } from './thresholdRules'
import { fanAutoMode } from './fanAuto'
import { editsOf } from './layoutData'
import { LEVEL } from '@/types/codes'

/* ───────── GET /equipment/tree?factoryId=5 ───────── */
export function equipmentTree() {
  const sensors = sensorsNow()
  const paint = EQUIPMENT.map((e) => ({
    id: e.id,
    name: e.name,
    type: e.type,
    sensorCount: sensors.filter((s) => s.equipmentId === e.id).length,
    tags: sensors.filter((s) => s.equipmentId === e.id).map((s) => s.tag), // 명세서·코드에 없음 (센서 번호 검색)
    level: equipmentLevel(e.id, sensors),
  }))
  // 프레스·차체·의장 공정은 설비 목록이 아직 없음 (설계안: '이 공정의 설비 목록은 준비 중입니다')
  return [
    { processId: 10, processName: '프레스 공정', equipment: [], equipmentCount: 9 },
    { processId: 11, processName: '차체 공정', equipment: [], equipmentCount: 12 },
    { processId: 12, processName: '도장 공정', equipment: paint, equipmentCount: paint.length },
    { processId: 13, processName: '의장 공정', equipment: [], equipmentCount: 11 },
  ]
}

/* ───────── GET /equipment/{id}/thresholds ───────── */

/** 센서 종류별 '근거' 문장 (설계안 NOTE) */
const BASIS = {
  CURRENT: (usual) => `평소 전류 ${usual} A 기준입니다. 기동 후 5초 동안의 순간 전류는 판정하지 않습니다.`,
  TEMP: () => '주의는 10초, 경보는 5초 이상 이어질 때 발생합니다.',
  CO: () => '경보 기준은 30 ppm보다 높게 정할 수 없습니다. (산업안전보건기준에 관한 규칙 제618조)',
}

const version = (equipmentId) => scenario.thresholdVersion?.[equipmentId] ?? 6

export function equipmentThresholds(equipmentId) {
  const e = EQUIPMENT.find((x) => x.id === Number(equipmentId))
  if (!e) return null
  const now = sensorsNow()
  return {
    equipmentId: e.id,
    equipmentName: e.name,
    equipmentType: e.type, // 명세서에 없음 (건조로 3대 함께 적용 단추)
    // 설비 설정 (2026-11-19 추가, 창 위쪽 스위치 2개): 사용 상태 · 배기팬 자동/수동 (배기팬이 없으면 null)
    active: editsOf(e.id).active ?? true,
    fanAuto: fanAutoMode(e.id),
    activeSeason: ACTIVE_SEASON,
    version: version(e.id),
    sensors: SENSOR_DEFS.filter((d) => d.equipmentId === e.id).map((d) => {
      const [max, usual] = GAUGE[d.tag]
      return {
        sensorId: d.id,
        tag: d.tag,
        type: d.type,
        unit: d.unit,
        value: now.find((s) => s.id === d.id)?.value ?? null,
        sampleSec: 1,
        setpoint: usual, // 평소 값 (백엔드 필드 이름 setpoint)
        max, // 눈금 끝 (명세서·코드에 없음: 경보 기준 위쪽 한계, 기준 띠 끝)
        seasons: Object.fromEntries(
          SEASONS.map((season) => [
            season,
            {
              ...seasonThreshold(d, season),
              ...lowOf(d.type, usual),
              warnHoldSec: HOLD_SEC[d.type].warn,
              alarmHoldSec: HOLD_SEC[d.type].alarm,
              validFrom: `2026-${SEASON_START[season]}`,
            },
          ]),
        ),
        legal: d.type === 'CO' ? { max: 30, text: '산업안전보건기준에 관한 규칙 제618조 적정공기 CO 30 ppm 미만' } : null,
        basis: BASIS[d.type](usual),
      }
    }),
  }
}

/**
 * 하한 기준 (DB sensor_threshold.low_warn · low_alarm)
 * - 전류만 있음: 평소 값의 90% 아래로 떨어지면 주의, 70% 아래면 경보 (벨트 끊김 · 정지) — DB 시드와 같은 규칙
 * - 온도 · CO 는 null. 화면에는 아직 입력 칸이 없고, 저장할 때 받은 값을 그대로 돌려보냄 (saveFlow.js)
 */
function lowOf(type, usual) {
  if (type !== 'CURRENT') return { lowWarn: null, lowAlarm: null }
  return { lowWarn: Number((usual * 0.9).toFixed(1)), lowAlarm: Number((usual * 0.7).toFixed(1)) }
}

/* ───────── 7일 그래프 (설계안 series(): 30분 간격, 평소 값 근처 + 넘었던 순간 3번) ───────── */

const pad = (n) => String(n).padStart(2, '0')
const STEPS = 6 * 48 + 33 // 6일 전 0시 ~ 오늘 16:30 (30분 간격)

/** d 일 전 + 하루 안의 30분 칸 → ISO */
function isoOf(step) {
  const d = new Date()
  d.setDate(d.getDate() - 6 + Math.floor(step / 48))
  const ymd = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  const h = Math.floor((step % 48) / 2)
  return `${ymd}T${pad(h)}:${step % 2 ? '30' : '00'}:00+09:00`
}

/** 센서 하나의 7일 값 + 넘었던 순간(peaks) */
function weekOf(sensorId) {
  const d = SENSOR_DEFS.find((x) => x.id === Number(sensorId))
  const usual = GAUGE[d.tag][1]
  const gap = d.warn - usual // 평소 → 주의 기준 거리
  // 같은 센서는 늘 같은 모양 (간단한 의사 난수)
  let x = 29 + d.tag.charCodeAt(0) + d.id
  const rnd = () => ((x = (x * 16807) % 2147483647) / 2147483647)
  const bumps = [
    [1 * 48 + 29, 1.19],
    [2 * 48 + 22, 0.86],
    [4 * 48 + 31, 0.89],
  ]
  // 상도 건조로가 지금 주의·경보면 마지막 칸이 지금 값
  const now = sensorsNow().find((s) => s.id === d.id)
  if (d.equipmentId === 31 && now && now.level !== LEVEL.NORMAL) bumps.push([STEPS, (now.value - usual) / gap])
  const dec = d.type === 'CURRENT' ? 1 : 0
  const round = (v) => Number(v.toFixed(dec))
  const values = []
  for (let i = 0; i <= STEPS; i++) {
    let v = usual + Math.sin(i / 7) * gap * 0.09 + (rnd() - 0.5) * gap * 0.16
    for (const [at, h] of bumps) v += h * gap * Math.exp(-(((i - at) / 1.6) ** 2))
    values.push(Number(v.toFixed(2)))
  }
  const peaks = bumps.map(([at, h]) => {
    const v = round(usual + h * gap)
    values[at] = v
    return { at: isoOf(at), value: v }
  })
  return {
    series: { sensorId: d.id, tag: d.tag, unit: d.unit, ...seasonThreshold(d), points: values.map((v, i) => [isoOf(i), v]) },
    peaks,
  }
}

export const weekSeries = (sensorId) => weekOf(sensorId).series

/* ───────── POST /thresholds/simulate ───────── */
export function simulate({ sensorId, warn, alarm }) {
  const d = SENSOR_DEFS.find((x) => x.id === Number(sensorId))
  const { peaks } = weekOf(sensorId)
  const cur = seasonThreshold(d)
  const count = (w, a) => ({ warnCount: peaks.filter((p) => p.value >= w).length, alarmCount: peaks.filter((p) => p.value >= a).length })
  const current = count(cur.warn, cur.alarm)
  const proposed = count(warn, alarm)
  return {
    current,
    proposed,
    diff: proposed.warnCount - current.warnCount,
    hits: peaks.filter((p) => p.value >= warn).map((p) => p.at),
    peaks, // 명세서에 없음: 그래프에 넘었던 순간 점을 찍으려고
  }
}

/* ───────── GET /thresholds/history ───────── */

/** 기본 이력 (설계안 THH 'top-oven' + 오늘 07:40 설비 상세 이벤트와 같은 줄) */
function seedHistory() {
  const row = (id, sensorId, sensorTag, at, by, before, after, reason) => ({
    id, sensorId, sensorTag, season: 'WINTER', before, after, changedBy: by, changedAt: at, reason,
  })
  return [
    row(7790, 117, 'CT-17', todayAt('07:40:00'), '오승민', { warn: 20.7, alarm: 27 }, { warn: 21.6, alarm: 27 }, '베어링 교체 완료, 겨울 기준으로 되돌림'),
    row(7785, 117, 'CT-17', '2026-11-10T14:20:00+09:00', '한지우', { warn: 21.6, alarm: 27 }, { warn: 20.7, alarm: 27 }, '베어링 교체 전까지 주의 기준을 낮춤'),
    row(7781, 215, 'TE-15', '2026-11-03T09:30:00+09:00', '이상훈', { warn: 160, alarm: 185 }, { warn: 160, alarm: 180 }, '건조로 과열 방지 기준 조정'),
    row(7760, 117, 'CT-17', '2026-10-06T14:02:00+09:00', '한지우', { warn: 22.5, alarm: 27 }, { warn: 21.6, alarm: 27 }, '평소 전류의 120%로 통일'),
    row(7742, 313, 'CO-13', '2026-09-15T10:12:00+09:00', '윤서연', { warn: 20, alarm: 35 }, { warn: 20, alarm: 30 }, '법정 적정공기 기준에 맞춤'),
    row(7700, 117, 'CT-17', '2026-08-01T09:00:00+09:00', '초기 설정', null, { warn: 22.5, alarm: 27 }, '설비 등록 때 기본값 적용'),
    row(7701, 215, 'TE-15', '2026-08-01T09:00:00+09:00', '초기 설정', null, { warn: 160, alarm: 185 }, '설비 등록 때 기본값 적용'),
    row(7702, 313, 'CO-13', '2026-08-01T09:00:00+09:00', '초기 설정', null, { warn: 20, alarm: 35 }, '설비 등록 때 기본값 적용'),
  ]
}

export function history({ equipmentId, from, to, sensorId, season }) {
  const ids = SENSOR_DEFS.filter((d) => d.equipmentId === Number(equipmentId)).map((d) => d.id)
  const day = (iso) => iso.slice(0, 10)
  // 저장한 기록은 반응형 객체라 일반 객체로 바꿔서 붙임 (structuredClone 실패 방지)
  const saved = (scenario.thresholdHistory ?? []).map((r) => ({ ...r, before: { ...r.before }, after: { ...r.after } }))
  return [...saved, ...seedHistory()]
    .filter((r) => ids.includes(r.sensorId))
    .filter((r) => (!from || day(r.changedAt) >= from) && (!to || day(r.changedAt) <= to))
    .filter((r) => (!sensorId || r.sensorId === Number(sensorId)) && (!season || r.season === season))
    .sort((a, b) => b.changedAt.localeCompare(a.changedAt))
}

/* ───────── PUT /equipment/{id}/thresholds ───────── */

/**
 * 저장 → { equipmentId, version, appliedNow } 또는 오류 { code, message, status }
 * - 서버 검사와 같은 규칙: 주의 < 경보, CO 경보 30 ppm 이하(법정), 판 번호(version) 충돌
 */
export function save(equipmentId, { season, version: v, reason, sensors }, by) {
  const id = Number(equipmentId)
  if (!reason?.trim()) return { error: { code: 'REASON_REQUIRED', message: '변경 사유를 적어 주세요.', status: 400 } }
  if (v !== version(id)) return { error: { code: 'THRESHOLD_CONFLICT', message: '다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.', status: 409 } }
  for (const s of sensors) {
    const d = SENSOR_DEFS.find((x) => x.id === s.sensorId)
    if (!(s.warn < s.alarm)) return { error: { code: 'WARN_NOT_BELOW_ALARM', message: '주의 기준은 경보 기준보다 낮아야 합니다.', status: 400 } }
    if (d.type === 'CO' && s.alarm > 30) return { error: { code: 'LOOSER_THAN_LEGAL', message: 'CO 경보 기준은 30 ppm보다 높게 정할 수 없습니다.', status: 400 } }
  }
  const at = todayAt(scenarioNow())
  for (const s of sensors) {
    const d = SENSOR_DEFS.find((x) => x.id === s.sensorId)
    const before = seasonThreshold(d, season)
    if (before.warn === s.warn && before.alarm === s.alarm) continue
    storeThreshold(d.id, season, { warn: s.warn, alarm: s.alarm })
    scenario.thresholdHistory = [
      { id: Date.now() % 1e6, sensorId: d.id, sensorTag: d.tag, season, before, after: { warn: s.warn, alarm: s.alarm }, changedBy: by, changedAt: at, reason },
      ...(scenario.thresholdHistory ?? []),
    ]
  }
  scenario.thresholdVersion = { ...scenario.thresholdVersion, [id]: version(id) + 1 }
  emitLive('threshold.changed')
  return { data: { equipmentId: id, version: version(id), appliedNow: season === ACTIVE_SEASON } }
}
