/**
 * 공정 3D 배치도 API (설계 자료 API 명세서 B3 '도장 공정 3D 배치도' 기준)
 * - GET  /processes/{processId}/layout : 설비·센서 목록 (잘 안 바뀌는 값)
 * - GET  /processes/{processId}/status : 지금 값·단계, 배기팬 상태
 * - PUT  /processes/{processId}/layout : 공장 관리 배치 저장 (명세서 H1, 백엔드 FactoryAdminController)
 * - (2026-11-19 배기팬 켜기·끄기·회전수 POST /equipment/{id}/fan 은 웹에서 안 씀 — 대시보드 배기팬 조절 창 삭제)
 * - GET  /inspections/today            : 오늘 설비 점검 목록
 * - POST /processes                    : 공정 추가 (공장 관리 공정 드롭다운 '공정 추가', 명세서 H1)
 */
import http from './http'
import { addProcess, addedLayout, addedProcessName, saveAddedLayout } from './mock/addedScope'
import { mockSiteTrees } from './mock/data'
import { saveLayout } from './mock/layoutData'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { mockProcess as paintProcess, sensorsNow } from './mock/processData'
import { LEVEL } from '@/types/codes'
import { autoCode } from './sites'

/**
 * 공정 예시 데이터: 도장 공정(12)은 processData.js, 공장 관리에서 추가한 공정은 설비 없는 빈 배치도
 * (실제 서버도 새 공정은 equipment · sensors 가 빈 배열로 옴)
 */
function mockProcess(processId) {
  const paint = paintProcess(processId)
  if (paint) return paint
  const name = addedProcessName(processId)
  if (!name) return null
  const id = Number(processId)
  const lay = addedLayout(id) // 공장 관리에서 놓고 저장한 설비 (처음엔 없음)
  return {
    layout: { processId: id, name, version: lay.version, floorplan: null, equipment: structuredClone(lay.equipment), sensors: [] },
    status: {
      processId: id,
      level: LEVEL.NORMAL,
      equipment: lay.equipment.map((e) => ({ id: e.id, level: LEVEL.NORMAL, runState: 'RUNNING', fan: null })),
      sensors: [],
    },
    inspections: { doneCount: 0, totalCount: 0, items: [] },
  }
}

const notFound = () => mockFail({ code: 'NOT_FOUND', message: '설비 배치도가 등록되지 않은 공정입니다.', status: 404 })

/** 배치도: { processId, name, equipment: [...], sensors: [...] } */
export function getProcessLayout(processId) {
  const data = mockProcess(processId)
  if (USE_MOCK) return data ? mockOk(data.layout) : notFound()
  return http.get(`/processes/${processId}/layout`)
}

/** 지금 값·단계: { processId, level, equipment: [{ id, level, runState, fan }], sensors: [{ id, tag, value, level, warn, alarm }] } */
export function getProcessStatus(processId) {
  const data = mockProcess(processId) // 부를 때마다 지금 예시 상황으로 만듦
  if (USE_MOCK) return data ? mockOk(data.status) : notFound()
  return http.get(`/processes/${processId}/status`)
}

/** 오늘 설비 점검: { doneCount, totalCount, items: [{ scheduleId, time, equipmentName, title, assignee, status }] } */
export function getTodayInspections(processId) {
  const data = mockProcess(processId)
  if (USE_MOCK) return mockOk(data?.inspections ?? { doneCount: 0, totalCount: 0, items: [] })
  return http.get('/inspections/today', { params: { processId } })
}

/**
 * 공장 관리: 배치 저장 (바꾼 것 한꺼번에, 하나라도 틀리면 전부 저장 안 됨)
 * - 값 이름은 DB equipment 테이블 · GET layout 응답과 같음
 *   · pos { x, y }: pos_x · pos_y — 배치도 화면 좌표(PaintMap 그림 좌표, 설비 가운데 점). 미터가 아님
 *   · type: equipment_type 코드 (OVEN · FAN · BURNER · TANK · PUMP · CAMERA)
 *   · active: is_active
 *   · shape(create 만): 배치도에 그릴 그림 이름 ('top-oven' · 'fan' …, features/process/paintLayout.js) — DB equipment.shape
 * - equipment.delete: 지울 설비 id 들 (서버는 센서가 붙은 설비는 지우지 않고 사용 안 함으로 바꿈)
 * @param {{ version, equipment: { create: [{ tempId, name, type, shape, pos }], update: [{ id, pos?: { x, y }, name?, type?, active? }], delete: number[] } }} body
 * @returns {{ version, changedCount }}
 */
export function saveProcessLayout(processId, body) {
  if (USE_MOCK) {
    // 공장 관리에서 추가한 공정은 addedScope.js, 도장 공정은 layoutData.js
    // (도장 공정 삭제 규칙: 센서가 붙은 설비는 지우지 않고 사용 안 함 → 센서 유무를 알려 줌)
    const hasSensors = (id) => sensorsNow().some((s) => s.equipmentId === id)
    const r = addedProcessName(processId) ? saveAddedLayout(processId, body) : saveLayout(processId, body, hasSensors)
    return r.error ? mockFail(r.error) : mockOk(r.data)
  }
  return http.put(`/processes/${processId}/layout`, body)
}

/**
 * 공정 추가 (POST /processes) — 본사 관리자 · 담당 공장의 공장 담당자
 * - tagPrefix(센서 태그 앞글자)는 비움 — 설비 · 센서를 추가할 때 정함 (DB 에서 비워도 됨)
 * @returns {Promise<{ id, name, hasFloorplan }>}
 */
export function createProcess({ factoryId, name }) {
  const body = { factoryId: Number(factoryId), code: autoCode('P'), name }
  if (USE_MOCK) {
    // 원래 있던 공장(예: 울산 1공장)에도 붙일 수 있게 예시 사업장 목록에서 공장을 찾음
    const find = (id) => Object.values(mockSiteTrees()).flatMap((tr) => tr.factories).find((f) => f.id === id)
    const r = addProcess(body, find)
    return r.error ? mockFail(r.error) : mockOk(r.data)
  }
  return http.post('/processes', body)
}
