/**
 * 예시 데이터 — 가스(CO) 알림 때 배기팬 자동 제어 (실제는 서버가 알림을 만들 때 함)
 * - 배기팬이 있는 설비: 전착 건조로 · 중도 부스 · 중도 건조로 · 상도 부스 · 상도 건조로 (processData.js fansNow 와 같음)
 * - 자동/수동: 공장 관리에서 설비마다 고름 (저장값 layoutData.js edits[id].fanAuto, 처음에는 모두 자동)
 * - 알림 응답에 붙는 fanAuto 모양 (GET /alarms items, GET /alarms/{id}/response):
 *   { equipmentId, fanName, mode: 'AUTO'|'MANUAL', percent, rpm, beforeRpm, status, commandId, at, manualHz }
 *   · manualHz: 담당자가 가스 주의 창에서 직접 정한 속도(Hz), 없으면 null (2026-11-20, 서버에 요청 중)
 *   · AUTO   → status APPLIED: percent·rpm 으로 바로 올림
 *   · MANUAL → status AWAITING: 담당자 승인 대기 (백엔드 API_IMPLEMENTED.md '배기팬 수동 승인')
 *              승인하면 APPLIED(실제는 SENT → APPLIED), 거절하면 DENIED
 */
import { editsOf } from './layoutData'
import { FAN_AUTO_PERCENT, FAN_HZ, FAN_MAX_RPM, FAN_NORMAL_RPM } from '@/types/codes'

/** 배기팬이 있는 설비 id → 배기팬 이름 */
export const FAN_EQUIPMENT = { 26: '전착 건조로 배기팬', 28: '중도 부스 배기팬', 29: '중도 건조로 배기팬', 30: '상도 부스 배기팬', 31: '상도 건조로 배기팬' }

/** 평소 회전수 — 30 Hz = 900 rpm (types/codes.js FAN_NORMAL_RPM, 2026-11-20 백엔드 피드백으로 1,200 → 900) */
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
  const base = { equipmentId, fanName: FAN_EQUIPMENT[equipmentId], beforeRpm: FAN_RATED_RPM, percent: FAN_AUTO_PERCENT[level], rpm: autoRpmOf(level), at }
  // 담당자가 속도를 직접 정했으면 그 값 (실제 서버는 아직 없음 — TODO(백엔드: fanAuto.manualHz, docs/api/fan-speed-control.md)
  base.manualHz = manualHz.get(equipmentId) ?? null
  if (auto) return { ...base, mode: 'AUTO', status: 'APPLIED', commandId: null }
  // 수동: 같은 알림(설비 + 발생 시각)이면 같은 명령 번호, 승인·거절한 뒤면 그 결과
  const commandId = commandIdOf(equipmentId, at, base.rpm)
  return { ...base, mode: 'MANUAL', status: decisions.get(commandId)?.status ?? 'AWAITING', commandId }
}

/* ───────── 수동 배기팬 승인 · 거절 (POST /fan-commands/{commandId}/approve · deny) ───────── */
const commandIds = new Map() // '설비id|발생시각' → 명령 번호
const commands = new Map() // 명령 번호 → { equipmentId, rpm(목표 회전수) }
const decisions = new Map() // 명령 번호 → { status } (승인 · 거절한 것만)
let nextCommandId = 8100

function commandIdOf(equipmentId, at, rpm) {
  const key = `${equipmentId}|${at}`
  if (!commandIds.has(key)) {
    commandIds.set(key, nextCommandId)
    commands.set(nextCommandId, { equipmentId, rpm })
    nextCommandId += 1
  }
  return commandIds.get(key)
}

/**
 * 승인 · 거절 처리 → { data } 또는 { error } (api/equipment.js 가 성공 · 실패 응답으로 바꿈)
 * - 예시에서는 승인하면 바로 APPLIED 로 남김 (실제는 응답 SENT → 엣지 결과 후 APPLIED)
 * @param {number} commandId
 * @param {boolean} approve
 */
export function decideFanCommand(commandId, approve) {
  const cmd = commands.get(commandId)
  if (!cmd) return { error: { code: 'COMMAND_NOT_FOUND', message: '없는 명령입니다.', status: 404 } }
  if (decisions.has(commandId)) return { error: { code: 'COMMAND_NOT_AWAITING', message: '이미 처리된 승인 요청입니다.', status: 409 } }
  decisions.set(commandId, { status: approve ? 'APPLIED' : 'DENIED' })
  return { data: { commandId, equipmentId: cmd.equipmentId, status: approve ? 'SENT' : 'DENIED', hz: cmd.rpm / 30, rpm: cmd.rpm } }
}

/* ───────── 배기팬 속도 직접 조절 (POST /equipment/{id}/fan { hz }, 2026-11-20) ───────── */
const manualHz = new Map() // 설비 id → 담당자가 직접 정한 Hz (새로고침하면 사라짐)

/**
 * 속도 직접 조절 → { data } 또는 { error } (서버 EquipmentActionService.fan 과 같은 검사)
 * - 예시에서는 바로 반영된 것으로 봄 (실제는 접수 202 → 엣지 결과 SSE command.result)
 * @param {number} equipmentId 배기팬이 달린 설비 id (예시는 건조로 id 로 받음 — 실제는 fanEquipmentId)
 * @param {number} hz
 */
export function setManualHz(equipmentId, hz) {
  if (!Number.isInteger(hz) || hz < FAN_HZ.MIN || hz > FAN_HZ.MAX) {
    return { error: { code: 'HZ_OUT_OF_RANGE', message: `주파수는 ${FAN_HZ.MIN} ~ ${FAN_HZ.MAX} Hz 사이여야 합니다.`, status: 400 } }
  }
  if (!FAN_EQUIPMENT[equipmentId]) return { error: { code: 'EQUIPMENT_NOT_FOUND', message: '없는 설비입니다.', status: 404 } }
  manualHz.set(equipmentId, hz)
  const commandId = nextCommandId++
  return { data: { commandId, equipmentId, status: 'PENDING' } }
}
