/**
 * 공장 관리 저장값 예시 (명세서 H1 PUT /processes/{processId}/layout — 백엔드에는 GET 만 있음)
 * - 설비별로 바뀐 것만 둠: { 설비 id: { pos: { x, y, z }, name, type, active, fanAuto } }
 *   · fanAuto: 배기팬 자동(true)/수동(false) — 가스 알림 때 배기팬 자동 제어 여부 (fanAuto.js)
 *   · 이름은 DB equipment 테이블 · 백엔드 layout 응답과 같음: pos = pos_x · pos_y(배치도 화면 좌표), type = equipment_type, active = is_active
 * - processData.js 가 GET layout 을 만들 때 이 값을 덮어씀 → 대시보드 배치도에도 바로 반영
 * - 상태는 이 파일 안에만 있음 (새로고침하면 처음으로)
 * - 2026-11-20 설비 추가 · 삭제: 추가한 설비는 addedScope.js (LAYOUTS[12]), 지운 원래 설비 id 는 LAYOUT.removed
 */
import { addedLayout, applyAdded } from './addedScope'

export const LAYOUT = {
  version: 14,
  /** { 설비 id: { pos, name, type, active } } */
  edits: {},
  /** 지운 설비 id (원래 설비 중 센서가 없어 실제로 지워진 것) */
  removed: [],
}

/** 설비 하나의 저장값 (없으면 빈 객체) */
export const editsOf = (id) => LAYOUT.edits[id] ?? {}

/**
 * PATCH /equipment/{equipmentId}/settings — 설비 설정 바로 저장 (2026-11-19, 설비 기준값 관리 창의 스위치 2개)
 * - 바꾼 값만 옴: { active?, fanAuto? }. 배치 저장과 따로라 배치 판 번호(version)는 올리지 않음
 *   (공장 관리에서 배치를 고치는 중에 저장해도 배치 저장이 409 로 막히지 않게)
 */
export function saveSettings(equipmentId, body) {
  const cur = { ...LAYOUT.edits[equipmentId] }
  if (body.active !== undefined) cur.active = body.active
  if (body.fanAuto !== undefined) cur.fanAuto = body.fanAuto
  LAYOUT.edits[equipmentId] = cur
  // 응답의 fanAuto 는 배기팬 유무를 아는 fanAuto.js 가 채움 (api/equipment.js)
  return { equipmentId: Number(equipmentId), active: cur.active ?? true }
}

/**
 * PUT /processes/{processId}/layout — 도장 공정 (12)
 * - update: 원래 설비는 edits 에, 추가한 설비는 그 줄을 바로 고침 (addedScope.js applyAdded)
 * - create: 새 설비 (shape = 그림 이름) → 응답 created.equipment { tempId: id }
 * - delete: 서버와 같은 규칙 (중앙 서버 MasterAdminService.removeEquipment)
 *   · 센서가 붙은 설비는 지우지 않고 사용 안 함(active = false), 센서가 없으면 실제로 지움
 * @param {{ version, equipment: { create: [], update: [], delete: [] } }} body
 * @param {(equipmentId: number) => boolean} hasSensors 센서가 붙은 설비인지 (processData.js 센서 목록으로 판단)
 */
export function saveLayout(processId, body, hasSensors = () => false) {
  if (Number(processId) !== 12) return { error: { code: 'NOT_FOUND', message: '배치도가 등록되지 않은 공정입니다.', status: 404 } }
  if (body.version !== LAYOUT.version) {
    return { error: { code: 'LAYOUT_CONFLICT', message: '다른 사람이 먼저 저장했습니다. 다시 불러와 주세요.', status: 409 } }
  }
  const extra = addedLayout(12)
  const added = extra.equipment.map((e) => e.id) // 공장 관리에서 추가한 설비 id (아래 applyAdded 가 따로 처리)

  const updates = (body.equipment?.update ?? []).filter((u) => !added.includes(u.id))
  for (const u of updates) {
    const cur = { ...LAYOUT.edits[u.id] }
    for (const f of ['name', 'type', 'active', 'fanAuto']) if (u[f] !== undefined) cur[f] = u[f]
    // 위치: 응답은 pos { x, y, z } (z 는 이 화면에서 안 바꿈)
    if (u.pos) cur.pos = { x: u.pos.x, y: u.pos.y, z: null }
    LAYOUT.edits[u.id] = cur
  }
  const deletes = (body.equipment?.delete ?? []).filter((id) => !added.includes(id))
  for (const id of deletes) {
    if (hasSensors(id)) LAYOUT.edits[id] = { ...LAYOUT.edits[id], active: false }
    else LAYOUT.removed.push(id)
  }
  const { createdIds, count } = applyAdded(extra, 12, body)

  LAYOUT.version += 1
  const changedCount = updates.length + deletes.length + count
  return { data: { version: LAYOUT.version, created: { equipment: createdIds, sensors: {} }, changedCount } }
}
