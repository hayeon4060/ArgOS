/**
 * 공정 3D 배치도 API (설계 자료 API 명세서 B3 '도장 공정 3D 배치도' 기준)
 * - GET  /processes/{processId}/layout : 설비·센서 목록 (잘 안 바뀌는 값)
 * - GET  /processes/{processId}/status : 지금 값·단계, 배기팬 상태
 * - PUT  /processes/{processId}/layout : 공장 관리 배치 저장 (명세서 H1, 백엔드 FactoryAdminController)
 * - (2026-11-19 배기팬 켜기·끄기·회전수 POST /equipment/{id}/fan 은 웹에서 안 씀 — 대시보드 배기팬 조절 창 삭제)
 * - GET  /inspections/today            : 오늘 설비 점검 목록
 */
import http from './http'
import { saveLayout } from './mock/layoutData'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { mockProcess } from './mock/processData'

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
 * @param {{ version, equipment: { create: [], update: [{ id, pos?: { x, y }, name?, type?, active? }], delete: [] } }} body
 * @returns {{ version, changedCount }}
 */
export function saveProcessLayout(processId, body) {
  if (USE_MOCK) {
    const r = saveLayout(processId, body)
    return r.error ? mockFail(r.error) : mockOk(r.data)
  }
  return http.put(`/processes/${processId}/layout`, body)
}
