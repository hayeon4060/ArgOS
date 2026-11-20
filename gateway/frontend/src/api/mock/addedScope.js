/**
 * 공장 관리에서 추가한 사업장 · 공장 · 공정 예시 (백엔드 없이 화면 확인용)
 * - 실제 API: POST /sites · POST /factories · POST /processes (백엔드 FactoryAdminController)
 * - 추가한 것은 이 파일 안에만 있음 → 새로고침하면 사라짐 (layoutData.js 와 같음)
 * - data.js 의 mockSites · mockSiteTrees 와 process.js 의 배치도가 이 목록을 같이 씀
 */
import { LEVEL } from '@/types/codes'

export const ADDED = {
  /** GET /sites 한 줄과 같은 모양 (주소 · 위경도 없음) */
  sites: [],
  /** GET /sites/{id}/tree 의 공장 한 줄 + siteId */
  factories: [],
  /** 원래 있던 공장(울산 1공장 등)에 추가한 공정 { factoryId, id, name, level, abnormal } */
  extraProcesses: [],
}

// 예시 데이터 id 와 겹치지 않게 큰 수부터
let nextId = 9001

/** 센서 단계 개수 0 (새로 만든 곳은 센서가 없음) */
const zero = () => ({ [LEVEL.NORMAL]: 0, [LEVEL.WARN]: 0, [LEVEL.ALARM]: 0 })

/** 백엔드와 같은 중복 에러 (DB 고유 키 위반 → 409 CODE_TAKEN) */
const taken = () => ({ error: { code: 'CODE_TAKEN', message: '같은 이름이나 코드가 이미 있습니다.', status: 409 } })

/**
 * POST /sites — 사업장 추가 (본사 관리자만)
 * @param {string[]} names 지금 있는 사업장 이름 (DB plant.name 이 고유)
 */
export function addSite(body, names) {
  if (names.includes(body.name)) return taken()
  const site = { id: nextId++, code: body.code, name: body.name, address: null, lat: null, lng: null }
  ADDED.sites.push(site)
  return { data: { id: site.id, name: site.name } }
}

/** POST /factories — 공장 추가 */
export function addFactory(body) {
  const factory = {
    siteId: Number(body.siteId),
    id: nextId++,
    code: body.code,
    name: body.name,
    level: LEVEL.NORMAL,
    levelsByType: { CURRENT: zero(), TEMP: zero(), CO: zero() },
    processes: [],
    todayAlarmCount: 0,
  }
  ADDED.factories.push(factory)
  return { data: { id: factory.id, name: factory.name } }
}

/** POST /processes — 공정 추가 (공장을 못 찾으면 404) */
export function addProcess(body, findFactory) {
  const factory = ADDED.factories.find((f) => f.id === Number(body.factoryId)) ?? findFactory(Number(body.factoryId))
  if (!factory) return { error: { code: 'NOT_FOUND', message: '공장을 찾을 수 없습니다.', status: 404 } }
  const process = { id: nextId++, name: body.name, level: LEVEL.NORMAL, abnormal: [] }
  // 원래 있던 공장(울산 1공장 등)에 붙인 공정은 따로 모아 둠 (data.js 가 그 공장에 이어 붙임)
  if (ADDED.factories.includes(factory)) factory.processes.push(process)
  else ADDED.extraProcesses.push({ factoryId: factory.id, ...process })
  return { data: { id: process.id, name: process.name, hasFloorplan: false } }
}

/** 이 사업장에 추가한 공장들 */
export const addedFactoriesOf = (siteId) => ADDED.factories.filter((f) => f.siteId === Number(siteId))

/** 원래 있던 공장에 추가한 공정들 */
export const extraProcessesOf = (factoryId) =>
  ADDED.extraProcesses.filter((p) => p.factoryId === factoryId).map(({ factoryId: _, ...p }) => p)

/* ───────── 추가한 공정의 배치 (GET · PUT /processes/{id}/layout) ─────────
 * - { 공정 id: { version, equipment: [GET layout 의 설비 한 줄] } } — 처음엔 설비 없음
 *   (도장 공정 12 에 추가한 설비도 LAYOUTS[12].equipment 에 둠 — 판 번호는 layoutData.js LAYOUT.version)
 * - 새 설비 code 는 서버처럼 'EQ-{공정 id}-{n}' (MasterAdminService.code)
 */
const LAYOUTS = {}

/** 추가한 공정의 배치 (없으면 빈 배치로 만듦) */
export function addedLayout(processId) {
  return (LAYOUTS[processId] ??= { version: 1, equipment: [] })
}

/**
 * 공장 관리에서 추가한 설비에 배치 저장 본문 적용 (추가한 공정, 도장 공정 12 에 추가한 설비 모두 여기)
 * - create: 새 설비 (tempId → 실제 id). shape = 그림 이름 (features/process/paintLayout.js), code 는 서버처럼 'EQ-{공정 id}-{n}'
 * - update: 이름 · 종류 · 사용 · 위치 / delete: 센서가 없으므로 바로 지움 (서버 MasterAdminService.removeEquipment 와 같음)
 * - 이 목록에 없는 id 는 건드리지 않음 (도장 공정의 원래 설비는 layoutData.js 가 처리)
 * @returns {{ createdIds: { [tempId]: id }, count: number }}
 */
export function applyAdded(lay, processId, body) {
  const eqBody = body.equipment ?? {}
  const mine = (id) => lay.equipment.some((e) => e.id === id)
  let count = 0
  for (const u of (eqBody.update ?? []).filter((x) => mine(x.id))) {
    const eq = lay.equipment.find((e) => e.id === u.id)
    for (const f of ['name', 'type', 'active']) if (u[f] !== undefined) eq[f] = u[f]
    if (u.pos) eq.pos = { x: u.pos.x, y: u.pos.y, z: null }
    count += 1
  }
  const gone = (eqBody.delete ?? []).filter(mine)
  lay.equipment = lay.equipment.filter((e) => !gone.includes(e.id))
  count += gone.length
  const createdIds = {}
  for (const c of eqBody.create ?? []) {
    const id = nextId++
    lay.seq = (lay.seq ?? 0) + 1 // 지워도 code 가 겹치지 않게 따로 셈
    lay.equipment.push({
      id,
      code: `EQ-${processId}-${lay.seq}`,
      name: c.name,
      type: c.type ?? null,
      shape: c.shape ?? null,
      seq: null,
      pos: { x: c.pos?.x ?? null, y: c.pos?.y ?? null, z: null },
      active: true,
      managers: [],
      fanAuto: c.type === 'FAN' ? true : null,
    })
    createdIds[c.tempId] = id
    count += 1
  }
  return { createdIds, count }
}

/** PUT /processes/{id}/layout — 공장 관리에서 추가한 공정 (도장 공정 12 는 layoutData.js) */
export function saveAddedLayout(processId, body) {
  const lay = addedLayout(processId)
  if (body.version !== lay.version) {
    return { error: { code: 'LAYOUT_CONFLICT', message: '다른 사람이 먼저 저장했습니다. 다시 불러와 주세요.', status: 409 } }
  }
  const { createdIds, count } = applyAdded(lay, processId, body)
  lay.version += 1
  return { data: { version: lay.version, created: { equipment: createdIds, sensors: {} }, changedCount: count } }
}

/** 추가한 공정 이름 (없으면 null) — 빈 배치도를 만들 때 씀 */
export function addedProcessName(processId) {
  const id = Number(processId)
  const all = [...ADDED.factories.flatMap((f) => f.processes), ...ADDED.extraProcesses]
  return all.find((p) => p.id === id)?.name ?? null
}
