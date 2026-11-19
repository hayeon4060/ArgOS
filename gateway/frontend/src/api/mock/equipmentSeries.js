/**
 * 설비 상세 '센서 추이' 예시 값 만들기 (GET /sensors/series 흉내)
 * - 화면설계안 js/lib.js mkSeries(), js/v-dash.js vEquip() series() 와 같은 식
 * - 예시 상황 시각: 오늘 16:42 (16:38:20 전류 주의 → 16:41:05 체크리스트 전송 → 지금 조치 중)
 *   실제 시계가 아니라 이 시각에 맞춰 만듦 → 다른 화면(알림 16:38:20, 값 22.4 A)과 맞음
 * - 상도 건조로: 16:30 부터 전류가 18 → 22.4 A 로 오르고, 온도·CO 도 조금 뒤따라 오름
 * - 다른 설비: 평소 값 근처에서 조금씩 흔들리다 지금 값으로 이어짐
 * - 전날(compare): 평소 값 근처의 평범한 하루
 * - 위 시각은 설계안 시각표. 예시 상황을 바꾼 순간을 '지금'으로 옮긴 만큼(scenario.shiftSec) 그래프도 같이 옮김
 */
import { scenario } from './scenario'

/** 설계안 시각표의 '지금' (분 단위, 0시부터) = 16:42 */
export const SCENARIO_MINUTE = 16 * 60 + 42
/** 설계안 시각표에서 그래프가 시작하는 16:00 (분) */
const DESIGN_START = 16 * 60

/** 설계안 시각을 지금 상황으로 옮긴 분 (scenario.js anchorAt 의 shiftSec) */
const shiftMin = () => Math.round((scenario.shiftSec ?? 0) / 60)
/** 예시 상황의 '지금' 시각 문자열 (열화상 촬영 시각 등) */
export const SCENARIO_TIME = '16:42:10'

const pad = (n) => String(n).padStart(2, '0')

/** 오늘(또는 며칠 전) 날짜 + 0시부터 분 → '2026-11-16T16:42:00+09:00' (0시 전·24시 넘음은 전날·다음 날로) */
function isoAt(minute, daysAgo = 0) {
  const dayOff = Math.floor(minute / 1440)
  minute -= dayOff * 1440
  const d = new Date()
  d.setDate(d.getDate() - daysAgo + dayOff)
  const ymd = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return `${ymd}T${pad(Math.floor(minute / 60))}:${pad(minute % 60)}:00+09:00`
}

/** 오늘 날짜의 시각 문자열 ('16:38:20' → ISO) */
export function todayIso(time) {
  const [h, m, s] = time.split(':').map(Number)
  return isoAt(h * 60 + m).replace(/:00\+09:00$/, `:${pad(s)}+09:00`)
}

/** 잔물결 (설계안과 같은 식, seed 로 센서마다 모양을 다르게) */
const wave = (i, seed = 0) => Math.sin(i * 1.3 + seed) * 0.5 + Math.cos(i * 0.7 + seed * 2) * 0.35

/** 센서 종류별 흔들림 크기 */
const AMP = { CURRENT: 0.4, TEMP: 1.2, CO: 0.5 }

/** 낮(08~20시)에는 평소 값, 밤에는 조금 낮게 (설비가 덜 돎) */
const dayFactor = (minute) => (minute >= 8 * 60 && minute < 20 * 60 ? 1 : 0.95)

/**
 * 상도 건조로 센서 값 (설계안 mkSeries '주의' 상황)
 * - i: 16:00 부터 몇 분째 (16:00 전은 음수 → 평소 값)
 */
function topOvenValue(type, minute) {
  const i = minute - (DESIGN_START + shiftMin())
  const n = wave(i)
  const f = dayFactor(minute)
  if (type === 'CURRENT') return i > 30 ? 18 + (22.4 - 18) * Math.min(1, (i - 30) / 8) + n * 0.3 : 18 * f + n * 0.5
  if (type === 'TEMP') return i > 32 ? 142 + (146 - 142) * Math.min(1, (i - 32) / 10) + n : 142 * f + n * 1.2
  return i > 36 ? 6 + (9 - 6) * Math.min(1, (i - 36) / 6) + n * 0.6 : 6 * f + n
}

/** 그 밖의 설비: 평소 값 근처 */
const usualValue = (sensor, minute, seed) =>
  sensor.usual * dayFactor(minute) + wave(minute - 16 * 60, seed) * AMP[sensor.type]

/** 범위별 시간 칸 (분): 1시간 = 1분 간격, 6시간·오늘 = 10분 간격 + 마지막은 지금 */
function minutesOf(range) {
  const now = SCENARIO_MINUTE + shiftMin()
  if (range === '1h') return Array.from({ length: 61 }, (_, k) => now - 60 + k)
  const start = range === '6h' ? now - 360 : 0
  const list = []
  for (let m = start - (start % 10); m < now; m += 10) list.push(m)
  list.push(now)
  return list
}

const round1 = (v) => Math.round(v * 10) / 10

/**
 * 주의 상세 창의 '전류 추이' (16:00 ~ 16:38, 1분 간격, 마지막은 주의 발생 값 22.4 A)
 * - GET /alarms/{id}/detail 의 trend.points 흉내 (설계안 trendA(): 최근 40분)
 */
export function alarmTrendPoints() {
  const start = DESIGN_START + shiftMin()
  const points = Array.from({ length: 39 }, (_, k) => [isoAt(start + k), round1(topOvenValue('CURRENT', start + k))])
  points[points.length - 1][1] = 22.4
  return points
}

/**
 * 주의 상세 창의 온도·CO 추이 (전류 추이와 같은 시각, 마지막은 주의 발생 값)
 * @param {'TEMP'|'CO'} type
 * @param {number} last 마지막 점 값
 */
export function alarmTrendPointsOf(type, last) {
  const start = DESIGN_START + shiftMin()
  const points = Array.from({ length: 39 }, (_, k) => [isoAt(start + k), Math.round(topOvenValue(type, start + k))])
  points[points.length - 1][1] = last
  return points
}

/**
 * 센서 추이 응답 만들기
 * @param {Array} sensors 설비 상세의 센서 목록 (id, tag, type, unit, value, usual, warn, alarm)
 * @param {boolean} rising 상도 건조로가 주의 상황인지 (16:30 부터 값이 오르는 그래프)
 * @param {'1h'|'6h'|'today'} range
 */
export function buildSeries(sensors, rising, range) {
  const minutes = minutesOf(range)
  // 전날: '오늘' 범위면 하루 전체, 아니면 같은 시각
  const prevMinutes = range === 'today' ? Array.from({ length: 144 }, (_, k) => k * 10) : minutes

  const series = sensors.map((s, idx) => {
    const seed = idx + 1
    const points = minutes.map((m) => [isoAt(m), round1(rising ? topOvenValue(s.type, m) : usualValue(s, m, seed))])
    // 마지막 세 점은 지금 값으로 자연스럽게 이어지게 (설계안과 같은 방식)
    const last = points.length - 1
    const from = points[Math.max(0, last - 3)][1]
    for (let j = 0; j < 3 && last - j >= 0; j++) {
      points[last - j][1] = round1(from + (s.value - from) * (1 - j / 3))
    }
    const compare = prevMinutes.map((m) => [isoAt(m, 1), round1(usualValue(s, m, seed + 4))])
    return { sensorId: s.id, tag: s.tag, unit: s.unit, warn: s.warn, alarm: s.alarm, points, compare }
  })
  return { step: range === '1h' ? '1m' : '10m', series }
}
