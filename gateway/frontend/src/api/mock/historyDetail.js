/**
 * 통계·이력 알림 상세 예시 데이터: GET /stats/alarms/{alarmId}
 * - 응답 모양은 docs/stats-history-api.md 와 같음
 * - 센서 시계열: 그 설비 센서 3종(전류·온도·CO), 처음 기준을 넘은 시각(raisedAt) ~ 조치 완료(closedAt, 조치 중이면 지금)
 *   30분 이하면 5초 간격, 넘으면 1분 간격
 * - 같은 알림은 열 때마다 같은 값 (알림 번호로 고정 난수)
 */
import { THERMAL_BY_STATE } from './equipmentData'
import { findStatsAlarm, ruleOfTag } from './statsData'
import { scenarioNow } from './scenario'

const TYPES = ['CURRENT', 'TEMP', 'CO']
const UNIT = { CURRENT: 'A', TEMP: '℃', CO: 'ppm' }
const TAG_HEAD = { CURRENT: 'CT', TEMP: 'TE', CO: 'CO' }
/** 다른 센서가 같이 오르는 정도 (알림 센서 → 나머지) */
const CORR = { CURRENT: { TEMP: 0.35, CO: 0.45 }, TEMP: { CURRENT: 0.3, CO: 0.2 }, CO: { TEMP: 0.3, CURRENT: 0.15 } }

const CHECK_ITEMS = {
  CURRENT: ['모터 소음·진동 확인', '구동 벨트 장력·마모 확인', '모터 표면 온도 확인 (열화상)', '배기 덕트 댐퍼 열림 확인', '전류 정상 복귀 확인'],
  TEMP: ['버너 화염 상태 확인', '온도 조절기 설정값 확인', '순환팬 가동 확인', '로 안 온도 다시 재기', '온도 정상 복귀 확인'],
  CO: ['주변 작업자 대피·출입 통제', '급배기팬 가동 상태 확인', '버너 불완전 연소 여부 확인', '휴대용 측정기로 CO 다시 재기', '환기 후 CO 정상 복귀 확인'],
}
const MEMO = {
  CURRENT: '벨트 장력이 약해 조정함. 다음 정기 점검 때 교체 요청.',
  TEMP: '온도 설정값이 바뀌어 있어 원래 값으로 되돌림.',
  CO: '배기 댐퍼가 반쯤 닫혀 있어 엶. 10분 뒤 CO 정상 확인.',
}
const SHUTDOWN_STEPS = ['버너 연료 차단', '컨베이어 정지', '배기팬 추가 가동 후 정지']

const pad = (n) => String(n).padStart(2, '0')
const iso = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}+09:00`
const smooth = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x))
const round = (v) => Math.round(v * 10) / 10

/** 알림 번호로 고정 난수 */
function rng(seed) {
  let s = seed % 2147483647 || 1
  return () => {
    s = (s * 16807) % 2147483647
    return s / 2147483647
  }
}

/** 센서별 [주의, 경보] 기준 (예시 설비 목록에 없으면 종류별 기본값) */
function ruleOf(type, tag, equipmentName) {
  const known = ruleOfTag(tag)
  if (known) return known
  if (type === 'CURRENT') return [21.6, 27]
  if (type === 'TEMP') return /건조로|오븐|소각/.test(equipmentName) ? [160, 180] : [60, 80]
  return [20, 30]
}

/** 같은 설비의 다른 센서 태그 (예시: 알림 센서 번호를 그대로 붙임) */
const tagOf = (a, type) => (type === a.sensorType ? a.sensorTag : `${TAG_HEAD[type]}-${a.sensorTag.split('-')[1] ?? '00'}`)

function series(a, r, start, end) {
  const spanSec = Math.max(60, (end - start) / 1000)
  const stepSec = spanSec <= 30 * 60 ? 5 : 60
  const n = Math.floor(spanSec / stepSec) + 1
  const times = Array.from({ length: n }, (_, i) => new Date(start.getTime() + i * stepSec * 1000))
  const closed = !!a.closedAt
  const equipmentName = a.location.split(' · ').at(-1)
  const sensors = TYPES.map((type, k) => {
    const tag = tagOf(a, type)
    const [warn, alarm] = ruleOf(type, tag, equipmentName)
    const trig = type === a.sensorType
    const usual = warn * (type === 'CO' ? 0.3 : 0.84)
    const noise = type === 'CO' ? 0.5 : usual * 0.01
    const points = times.map((t, i) => {
      const p = i / Math.max(1, n - 1) // 0 = 처음 넘은 시각, 1 = 조치 완료
      let v
      if (trig) {
        // 처음 기준값 → 25% 지점 최고값 → 조치 완료 때 주의 기준 아래로 (조치 중이면 최고값 근처 유지)
        const start0 = a.threshold
        const peak = Math.max(a.value, a.threshold) * 1.02
        const endV = warn * 0.92
        v = p < 0.25 ? start0 + (peak - start0) * smooth(p / 0.25) : closed ? peak + (endV - peak) * smooth((p - 0.25) / 0.75) : peak
      } else {
        const bump = (warn - usual) * (CORR[a.sensorType]?.[type] ?? 0) * (a.level === 'ALARM' ? 1.3 : 1)
        v = usual + bump * Math.sin(Math.PI * Math.min(1, p * 1.1))
      }
      // 알림 센서의 첫 값 = 기준을 처음 넘은 그 값 (흔들림 없이)
      if (!(trig && i === 0)) v += (r() - 0.5) * 2 * noise
      return [iso(t), round(v)]
    })
    return { sensorId: a.id * 10 + k, tag, type, unit: UNIT[type], warn, alarm, trigger: trig, points }
  })
  return { from: iso(start), to: iso(end), intervalSec: stepSec, sensors }
}

function timeline(a, start, end) {
  const at = (min) => iso(new Date(start.getTime() + min * 60000))
  const name = { CURRENT: '전류', TEMP: '온도', CO: 'CO' }[a.sensorType]
  const list = [{ at: a.raisedAt, step: 'RAISED', by: null, text: `${a.level === 'WARN' ? '주의' : '경보'} 발생 · ${name} ${a.value} ${a.unit} (기준 ${a.threshold})` }]
  if (a.checklist) {
    list.push({ at: at(0.6), step: 'ACK', by: a.ackedBy, text: '확인' })
    list.push({ at: at(1), step: 'CHECKLIST', by: a.ackedBy, text: `체크리스트 전송 → ${a.assignee ?? '공정 담당자'}` })
    if (a.closedAt) {
      list.push({ at: iso(new Date(end.getTime() - 60000)), step: 'REPORTED', by: a.assignee, text: '완료 보고' })
      list.push({ at: a.closedAt, step: 'CLOSED', by: a.ackedBy, text: '조치 확인 · 주의 해제' })
    }
  } else {
    list.push({ at: at(0.3), step: 'SHUTDOWN', by: a.ackedBy, text: '설비 셧다운 요청' })
    if (a.closedAt) list.push({ at: a.closedAt, step: 'CLOSED', by: a.ackedBy, text: '정상 범위 복귀 · 경보 해제' })
  }
  return list
}

function checklist(a, start, end) {
  if (!a.checklist) return null
  const { doneCount, totalCount } = a.checklist
  const span = Math.max(3, (end - start) / 60000 - 2)
  return {
    assignee: a.assignee,
    doneCount,
    totalCount,
    items: CHECK_ITEMS[a.sensorType].slice(0, totalCount).map((content, i) => {
      const done = i < doneCount
      return {
        seq: i + 1,
        content,
        doneAt: done ? iso(new Date(start.getTime() + (1.5 + (span / totalCount) * (i + 1)) * 60000)) : null,
        result: done ? (i === 1 ? '조치함' : '이상 없음') : null,
        memo: done && i === 1 ? MEMO[a.sensorType] : null,
      }
    }),
  }
}

function shutdown(a, start, end) {
  if (a.checklist) return null
  const span = Math.max(0.6, Math.min(3, (end - start) / 60000 - 0.5))
  return { steps: SHUTDOWN_STEPS.map((label, i) => ({ label, at: iso(new Date(start.getTime() + (0.4 + (span / 3) * (i + 1)) * 60000)) })) }
}

/** GET /stats/alarms/{alarmId} 예시 */
export function historyDetail(alarmId) {
  const a = findStatsAlarm(alarmId)
  if (!a) return null
  const r = rng(a.id * 13 + 7)
  const start = new Date(a.raisedAt)
  const end = a.closedAt ? new Date(a.closedAt) : new Date(scenarioNow())
  const t = THERMAL_BY_STATE[a.level] ?? THERMAL_BY_STATE.WARN
  const minutes = a.actionMinutes ?? Math.max(1, Math.round((end - start) / 60000))
  return {
    id: a.id,
    level: a.level,
    status: a.status,
    hazard: a.hazard,
    location: a.location,
    factoryId: a.factoryId,
    processId: a.processId,
    equipmentId: a.equipmentId,
    raisedAt: a.raisedAt,
    closedAt: a.closedAt ?? null,
    actionMinutes: minutes,
    sensorTag: a.sensorTag,
    sensorType: a.sensorType,
    value: a.value,
    unit: a.unit,
    threshold: a.threshold,
    ackedBy: a.ackedBy ?? null,
    series: series(a, r, start, end),
    thermal: { imageUrl: t.image, capturedAt: a.raisedAt, max: t.max, avg: t.avg, maxPoint: t.point, cameraTag: 'IR-01' },
    timeline: timeline(a, start, end),
    checklist: checklist(a, start, end),
    shutdown: shutdown(a, start, end),
  }
}
