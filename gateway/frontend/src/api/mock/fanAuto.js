/**
 * 예시 데이터 — 가스(CO) 알림 때 배기팬 자동 제어 (실제는 서버가 알림을 만들 때 함)
 * - 배기팬이 있는 설비: 전착 건조로 · 중도 부스 · 중도 건조로 · 상도 부스 · 상도 건조로 (processData.js fansNow 와 같음)
 * - 자동/수동: 공장 관리에서 설비마다 고름 (저장값 layoutData.js edits[id].fanAuto, 처음에는 모두 자동)
 * - 알림 응답에 붙는 fanAuto 모양 (GET /alarms items, GET /alarms/{id}/response):
 *   { equipmentId, fanName, mode: 'AUTO'|'MANUAL', percent, rpm, beforeRpm, status: 'APPLIED'|'SKIPPED', at }
 *   · AUTO  → status APPLIED: percent·rpm 으로 올림
 *   · MANUAL → status SKIPPED: 바꾸지 않음 (화면은 안내만)
 */
import { editsOf } from './layoutData'
import { FAN_AUTO_PERCENT, FAN_MAX_RPM, FAN_NORMAL_RPM } from '@/types/codes'

/** 배기팬이 있는 설비 id → 배기팬 이름 */
export const FAN_EQUIPMENT = { 26: '전착 건조로 배기팬', 28: '중도 부스 배기팬', 29: '중도 건조로 배기팬', 30: '상도 부스 배기팬', 31: '상도 건조로 배기팬' }

/** 평소 회전수 — 최종 ERD 기준 40 Hz = 1,200 rpm (전에는 1,180 으로 적어 둠) */
export const FAN_RATED_RPM = FAN_NORMAL_RPM

/** 이 설비 배기팬이 자동인지 (배기팬이 없으면 null) */
export function fanAutoMode(equipmentId) {
  if (!FAN_EQUIPMENT[equipmentId]) return null
  return editsOf(equipmentId).fanAuto ?? true
}

/** 단계별 자동 회전수 (주의 75% → 1,350 rpm, 경보 100% → 1,800 rpm) */
export const autoRpmOf = (level) => Math.round((FAN_MAX_RPM * FAN_AUTO_PERCENT[level]) / 100)

/**
 * 가스 알림 한 건의 배기팬 자동 제어 결과 (배기팬이 없거나 주의·경보가 아니면 null)
 * @param {{ equipmentId: number, level: 'WARN'|'ALARM', at: string }} p at = 알림 발생 시각(ISO)
 */
export function fanAutoOf({ equipmentId, level, at }) {
  const auto = fanAutoMode(equipmentId)
  if (auto === null || !FAN_AUTO_PERCENT[level]) return null
  const base = { equipmentId, fanName: FAN_EQUIPMENT[equipmentId], beforeRpm: FAN_RATED_RPM, at }
  if (!auto) return { ...base, mode: 'MANUAL', percent: null, rpm: null, status: 'SKIPPED' }
  return { ...base, mode: 'AUTO', percent: FAN_AUTO_PERCENT[level], rpm: autoRpmOf(level), status: 'APPLIED' }
}
