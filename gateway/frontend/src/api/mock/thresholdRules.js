/**
 * 예시 데이터 — 센서 기준값 규칙 (계절별 기준 + 기준값 관리 화면에서 저장한 값)
 * - 기본(겨울) 기준은 processData.js 의 센서 줄 값
 * - 계절: 봄·가을 / 여름 / 겨울(지금 적용 중) — 화면설계안 vThreshold() SEA
 *   온도 주의 기준만 봄·가을 3 ℃, 여름 6 ℃ 낮음 (소각설비처럼 400 ℃ 넘는 기준은 5배)
 * - 저장한 값은 예시 상황(scenario.thresholds)에 남아 새로고침해도 이어지고,
 *   지금 계절 값은 모든 화면의 단계·게이지·기준선에 바로 쓰임 (processData.js sensorsNow)
 */
import { scenario } from './scenario'

export const SEASONS = ['SPRING_FALL', 'SUMMER', 'WINTER']
/** 지금 적용 중인 계절 (11월 → 겨울) */
export const ACTIVE_SEASON = 'WINTER'
/** 계절이 시작되는 날 (명세서에 없음, 설계안 '계절이 바뀌는 날 0시에 자동으로 바뀝니다') */
export const SEASON_START = { SPRING_FALL: '03-01', SUMMER: '06-01', WINTER: '11-01' }

/** 지속 조건 (초): 이 시간 넘게 이어져야 알림 — 전류 3분/30초, 온도 10초/5초, CO 1분/10초 */
export const HOLD_SEC = {
  CURRENT: { warn: 180, alarm: 30 },
  TEMP: { warn: 10, alarm: 5 },
  CO: { warn: 60, alarm: 10 },
}

/** 계절 기본값 (저장한 값이 없을 때) */
function seasonDefault({ type, warn, alarm }, season) {
  if (type !== 'TEMP' || season === 'WINTER') return { warn, alarm }
  const step = (season === 'SUMMER' ? 6 : 3) * (warn > 400 ? 5 : 1)
  return { warn: warn - step, alarm }
}

/**
 * 센서 한 개의 계절 기준 { warn, alarm }
 * @param {{ id, type, warn, alarm }} sensor 기본(겨울) 기준이 든 센서 줄
 */
export function seasonThreshold(sensor, season = ACTIVE_SEASON) {
  // 복사본으로 돌려줌 (반응형 객체가 섞이면 mockOk 의 structuredClone 이 실패함)
  return { ...(scenario.thresholds?.[sensor.id]?.[season] ?? seasonDefault(sensor, season)) }
}

/** 저장 (예시 상황에 남김) */
export function storeThreshold(sensorId, season, { warn, alarm }) {
  if (!scenario.thresholds) scenario.thresholds = {}
  scenario.thresholds[sensorId] = { ...scenario.thresholds[sensorId], [season]: { warn, alarm } }
}
