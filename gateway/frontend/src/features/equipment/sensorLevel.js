/**
 * 설비 상세에 보여 줄 단계 정하기 (2026-11-20)
 * - 문제: 게이지·추이·상태 배지가 서버가 준 단계(level)만 보고 색을 정해서,
 *   값이 경보 기준을 넘어도(예: 온도 164 ℃ > 경보 160 ℃) 서버 단계가 아직 정상이면 초록으로 보였음
 *   (서버 단계는 엣지가 '연속 몇 번' 넘는 것을 보고 따로 보내 줘서 값보다 늦거나 안 올 수 있음)
 * - 규칙: 보여 줄 단계 = 서버 단계와 '지금 값을 기준값과 비교한 단계' 중 더 높은 쪽
 *   · 설계안 vEquip() 도 게이지·추이 색을 지금 값으로 정함 (lvS(기준, 값))
 *   · 서버가 주의·경보·화재를 유지 중이면 값이 잠깐 내려가도 그 단계를 그대로 보여 줌 (낮추지 않음)
 * - 기준 비교: 위쪽 기준(warn·alarm)은 '이상', 아래쪽 기준(lowWarn·lowAlarm)은 '이하' (central 판정과 같은 방향)
 */
import { LEVEL } from '@/types/codes'

const ORDER = [LEVEL.NORMAL, LEVEL.WARN, LEVEL.ALARM, LEVEL.FIRE]

/** 두 단계 중 더 높은 쪽 (모르는 값은 정상으로 봄) */
export function worseLevel(a, b) {
  return ORDER.indexOf(b) > ORDER.indexOf(a) ? b : (a ?? LEVEL.NORMAL)
}

/** 숫자인지 (null · undefined · NaN 은 '기준 없음'·'값 없음') */
const has = (v) => typeof v === 'number' && Number.isFinite(v)

/**
 * 지금 값만 보고 정한 단계
 * @param {{ warn?: number, alarm?: number, lowWarn?: number, lowAlarm?: number }} sensor 기준값
 * @param {number|null} value 지금 값 (없으면 정상)
 */
export function valueLevel(sensor, value) {
  if (!has(value)) return LEVEL.NORMAL
  if ((has(sensor.alarm) && value >= sensor.alarm) || (has(sensor.lowAlarm) && value <= sensor.lowAlarm)) return LEVEL.ALARM
  if ((has(sensor.warn) && value >= sensor.warn) || (has(sensor.lowWarn) && value <= sensor.lowWarn)) return LEVEL.WARN
  return LEVEL.NORMAL
}

/**
 * 화면용 설비: 센서마다 보여 줄 단계(level)를 다시 정하고, 설비 단계도 센서 중 가장 높은 단계까지 올림
 * - 받은 equipment 는 바꾸지 않고 새 객체를 돌려줌
 * @param {object} equipment GET /equipment/{id} 응답
 * @param {{ [sensorId]: number }} liveValues 실시간 값 (없으면 받은 value)
 */
export function withShownLevels(equipment, liveValues = {}) {
  const sensors = equipment.sensors.map((s) => ({
    ...s,
    level: worseLevel(s.level, valueLevel(s, liveValues[s.id] ?? s.value)),
  }))
  const level = sensors.reduce((lv, s) => worseLevel(lv, s.level), equipment.level)
  return { ...equipment, level, sensors }
}
