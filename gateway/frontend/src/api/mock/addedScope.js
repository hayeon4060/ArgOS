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

/** 추가한 공정 이름 (없으면 null) — 빈 배치도를 만들 때 씀 */
export function addedProcessName(processId) {
  const id = Number(processId)
  const all = [...ADDED.factories.flatMap((f) => f.processes), ...ADDED.extraProcesses]
  return all.find((p) => p.id === id)?.name ?? null
}
