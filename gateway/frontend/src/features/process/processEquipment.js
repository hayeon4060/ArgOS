/**
 * 화면용 설비 목록 만들기: 배치도(layout) + 지금 값(status) 을 합침
 * - 공정 배치도(ProcessPage)와 설비 상세(EquipmentPage, 설비 위치 그림)가 같이 씀
 * - key: 그림 안 설비 이름 (code 'TC-OVEN' → 'top-oven'), 공장 관리에서 추가한 설비는 'x' + id — paintLayout.js drawingOf
 * - 실제 서버는 한 그림에 설비가 여러 대(건조로 + 배기팬 + 버너실 …) → 그림용으로는 mergeByKey 로 묶어 씀
 * - sensors: 이 설비에 붙은 센서 + 지금 값·단계
 */
import { drawingOf, hiddenDrawings } from './paintLayout'
import { LEVEL } from '@/types/codes'

/**
 * 공장 관리에서 저장한 배치 → 배치도(PaintMap) 값
 * - positions: layout 설비의 pos { x, y } (DB pos_x · pos_y, 배치도 화면 좌표). 값이 없는 설비는 빼서 그림 그대로
 * - inactive: 사용 안 함 설비 (DB is_active = false)
 * - hidden: 숨길 설비 그림 — 그림에는 있지만 등록되지 않은 설비 (paintLayout.js hiddenDrawings)
 * @returns {{ positions: { [설비 이름]: { x, y } }, inactive: string[], hidden: string[] }}
 */
export function layoutPlacement(layout) {
  const all = (layout?.equipment ?? []).map((e) => ({ ...e, ...drawingOf(e) }))
  // 그림 하나에 설비가 여러 대면 대표 설비의 위치 · 사용 여부만 씀 (배기팬 · 버너실 값이 덮어쓰지 않게)
  const list = mergeByKey(all)
  return {
    hidden: hiddenDrawings(all),
    positions: Object.fromEntries(list.filter((e) => e.pos?.x != null && e.pos?.y != null).map((e) => [e.key, { x: e.pos.x, y: e.pos.y }])),
    inactive: list.filter((e) => e.active === false).map((e) => e.key),
  }
}

const LEVEL_ORDER = [LEVEL.NORMAL, LEVEL.WARN, LEVEL.ALARM, LEVEL.FIRE]
const worse = (a, b) => (LEVEL_ORDER.indexOf(b) > LEVEL_ORDER.indexOf(a) ? b : a)

/**
 * 그림 이름(key)이 같은 설비를 하나로 묶음 (배치도 그림용)
 * - 대표: seq(공정 흐름 순서)가 있는 설비, 없으면 첫 번째 (실제 서버는 탱크 · 건조로에만 seq 1~5)
 * - 단계: 묶인 설비 중 가장 높은 단계 (배기팬만 경보여도 그림은 경보), 센서: 묶인 설비 센서 전부
 * - 서버가 설비 단계를 관제 단위로 합쳐 주지 않아서(설비마다 따로) 화면에서 합침
 *   TODO(백엔드): GET /processes/{id}/status 가 관제 단위로 합친 단계를 주면 여기서 합치지 않아도 됨 (선택, backend-feedback-1120.md 5장)
 * - 예시 데이터는 그림마다 설비가 한 대라 그대로 나옴
 * @param {Array<{ key: string, seq?: number, level?: string, sensors?: Array }>} list
 */
export function mergeByKey(list) {
  const groups = {}
  for (const e of list) (groups[e.key] ??= []).push(e)
  return Object.values(groups).map((members) => {
    const main = members.find((m) => m.seq != null) ?? members[0]
    if (members.length === 1) return main
    return {
      ...main,
      level: members.reduce((lv, m) => worse(lv, m.level ?? LEVEL.NORMAL), LEVEL.NORMAL),
      sensors: members.flatMap((m) => m.sensors ?? []),
    }
  })
}

export function buildProcessEquipment(layout, status) {
  if (!layout || !status) return []
  const sensorNow = (id) => status.sensors.find((s) => s.id === id) ?? {}
  return layout.equipment.map((e) => {
    const now = status.equipment.find((x) => x.id === e.id) ?? {}
    return {
      id: e.id,
      ...drawingOf(e), // key(배치도 안 이름) · shape(그림 이름)
      code: e.code,
      name: e.name,
      seq: e.seq,
      level: now.level ?? LEVEL.NORMAL,
      fan: now.fan ?? null,
      sensors: layout.sensors.filter((s) => s.equipmentId === e.id).map((s) => ({ ...s, ...sensorNow(s.id) })),
    }
  })
}
