/**
 * 공장 관리 — 저장 전 변경 모으기 (화면설계안 v-pages2.js vLayout() changes())
 * - saved: GET layout 의 설비 (저장된 값), draft: 화면에서 고친 값 { 설비 id: { pos, name, type, active } }
 * - 값 이름은 DB 명세(equipment 테이블) · 백엔드 layout 응답과 같음
 *   · pos { x, y }: equipment.pos_x · pos_y — 배치도 화면 좌표 (PaintMap 그림 좌표). 비어 있으면 그림에 그려진 자리
 *   · type: equipment.equipment_type 코드 (OVEN · FAN …) — 화면에는 한글 이름
 *   · active: equipment.is_active
 *   (배기팬 자동 제어 equipment.fan_auto 는 2026-11-19부터 '설비 기준값 관리' 창에서 바로 저장 — 여기서는 안 다룸)
 * - 변경 줄: 위치 이동(좌표 + 함께 옮겨지는 센서), 이름 변경, 정보 변경(종류 · 사용 상태)
 * - 저장 본문: PUT /processes/{id}/layout 의 equipment.update
 * - 2026-11-20: 새 설비(저장 전, created) — 변경 줄 '○○ 추가', 저장 본문 equipment.create
 *   새 설비 한 줄: { id: 'n1'(tempId), tempId, isNew: true, key, shape, name, type, active, pos, sensors: [], managers: [] }
 *   · shape: 어떤 그림으로 그릴지 (paintLayout.js 의 그림 이름 'top-oven' · 'fan' …) → DB equipment.shape
 * - 2026-11-20: 설비 삭제(저장 전, deleted: 설비 id 들) — 변경 줄 '○○ 삭제', 저장 본문 equipment.delete
 */

/**
 * 설비 종류 (equipment.equipment_type 코드 → 화면 이름)
 * - 코드는 지금 돌아가는 DB 시드와 체크리스트 양식에 실제로 있는 값만 둠
 *   (factory/central-server V2__seed_ulsan_paint.sql, gateway V2__gateway_alarm_response.sql)
 * - 부스 · 실링 · 공조기 · 도료 혼합실 · 소각설비 같은 설비의 코드는 정해지지 않아 넣지 않음 (backend-notes 15장 질문)
 */
export const EQUIPMENT_TYPES = [
  { code: 'OVEN', label: '건조로' },
  { code: 'FAN', label: '배기팬' },
  { code: 'BURNER', label: '버너실' },
  { code: 'TANK', label: '탱크' },
  { code: 'PUMP', label: '펌프' },
  { code: 'CAMERA', label: '열화상 카메라' },
]

/** 종류 코드 → 화면 이름 (목록에 없는 코드는 코드 그대로, 없으면 '정해지지 않음') */
export function typeLabel(code) {
  if (!code) return '정해지지 않음'
  return EQUIPMENT_TYPES.find((t) => t.code === code)?.label ?? `${code} (목록에 없는 종류)`
}

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b)
/** 응답 pos { x, y, z } → 저장된 위치 { x, y } (값이 없으면 null) */
const posOf = (p) => (p?.x != null && p?.y != null ? { x: p.x, y: p.y } : null)
const fmt = (v) => String(Math.round(v))

/** 설비 하나의 지금 값 (저장값 위에 고친 값) */
export function currentOf(eq, draft) {
  return { pos: posOf(eq.pos), name: eq.name, type: eq.type ?? null, active: eq.active !== false, ...draft[eq.id] }
}

/** 화면에 쓰는 위치: 저장된(또는 고친) 위치, 없으면 그림에 그려진 자리 (defaults: PaintMap.defaultPositions) */
export const placeOf = (pos, key, defaults) => pos ?? defaults[key] ?? null

/**
 * 변경 목록
 * @param {Array} equipment GET layout 의 equipment (+ key, sensors)
 * @param {object} draft
 * @param {object} defaults 설비 이름(key) → 그려진 자리 { x, y }
 * @returns {Array<{ id, key, type, title, detail }>}
 */
export function changesOf(equipment, draft, defaults = {}, created = [], deleted = []) {
  const out = created.map((c) => ({
    id: c.id,
    key: c.key,
    type: 'add',
    title: `${c.name} 추가`,
    detail: `${typeLabel(c.type)} · X ${fmt(c.pos.x)} · Y ${fmt(c.pos.y)}`,
  }))
  for (const eq of equipment) {
    // 지울 설비는 '삭제' 한 줄만 (옮기거나 고친 것은 의미가 없어짐)
    if (deleted.includes(eq.id)) {
      const n = (eq.sensors ?? []).length
      out.push({ id: eq.id, key: eq.key, type: 'del', title: `${eq.name} 삭제`, detail: n ? `센서 ${n}개가 있어 '사용 안 함'으로 바뀝니다` : '배치도에서 지웁니다' })
      continue
    }
    const now = currentOf(eq, draft)
    const was = currentOf(eq, {})
    if (!same(now.pos, was.pos)) {
      const a = placeOf(was.pos, eq.key, defaults)
      const b = placeOf(now.pos, eq.key, defaults)
      const where = a && b ? `X ${fmt(a.x)} → ${fmt(b.x)} · Y ${fmt(a.y)} → ${fmt(b.y)}` : '위치 바꿈'
      const tags = (eq.sensors ?? []).map((s) => s.tag)
      out.push({ id: eq.id, key: eq.key, type: 'move', title: `${now.name} 위치 이동`, detail: where + (tags.length ? ` · 센서 ${tags.join(', ')} 함께 이동` : '') })
    }
    if (now.name !== was.name) out.push({ id: eq.id, key: eq.key, type: 'name', title: '설비 이름 변경', detail: `${was.name} → ${now.name}` })
    const meta = []
    if (now.type !== was.type) meta.push(`종류 ${typeLabel(now.type)}`)
    if (now.active !== was.active) meta.push(now.active ? '사용 상태 사용' : '사용 상태 사용 안 함')
    if (meta.length) out.push({ id: eq.id, key: eq.key, type: 'meta', title: `${now.name} 정보 변경`, detail: meta.join(' · ') })
  }
  return out
}

/**
 * 저장 본문 (바뀐 설비만) — PUT /processes/{id}/layout
 * - update 한 줄: { id, pos: { x, y, z }, name, type, active } (pos 는 정해진 경우만)
 */
export function bodyOf(version, equipment, draft, created = [], deleted = []) {
  const update = equipment
    .filter((eq) => !deleted.includes(eq.id) && draft[eq.id] && !same(currentOf(eq, draft), currentOf(eq, {})))
    .map((eq) => {
      const now = currentOf(eq, draft)
      // 화면 반응형 값이라 보통 객체로 복사 (예시 데이터가 structuredClone 으로 복사함)
      const row = { id: eq.id, name: now.name, type: now.type, active: now.active }
      // 높이(z)는 화면에서 안 바꾸지만 저장된 값을 그대로 실어 보냄 — 서버는 pos 를 받으면 x · y · z 를 함께 덮어써서, 빼면 z 가 지워짐 (2차 검증)
      if (now.pos) row.pos = { x: now.pos.x, y: now.pos.y, z: eq.pos?.z ?? null }
      // TODO(백엔드): 중앙 서버 배치 저장이 update 의 type(설비 종류)을 아직 반영하지 않음 → docs/api/frontend-api-audit-1120.md 9장
      return row
    })
  // 새 설비: tempId 는 응답 created.equipment 에서 실제 id 로 바뀜. code 는 비워 보내면 서버가 'EQ-{공정 id}-{n}' 로 만듦
  // shape: 그림 이름 — 다시 불러왔을 때 같은 그림으로 그리려고 DB equipment.shape 에 둠
  const create = created.map((c) => ({ tempId: c.tempId, name: c.name, type: c.type, shape: c.shape, pos: { x: c.pos.x, y: c.pos.y, z: null } }))
  // 삭제: 서버는 센서가 붙은 설비는 지우지 않고 사용 안 함으로 바꿈 (중앙 서버 MasterAdminService.removeEquipment)
  return { version, equipment: { create, update, delete: [...deleted] }, sensors: { create: [], update: [], delete: [] } }
}

/** 새 설비(저장 전) 한 줄 만들기 — key 는 저장 전 임시 이름('x' + 임시 id, paintLayout.js drawingOf 와 같은 꼴) */
export function newEquipment(tempId, { name, type, shape }, pos) {
  return { id: tempId, tempId, isNew: true, key: `x${tempId}`, shape, name, type, active: true, pos, sensors: [], managers: [] }
}
