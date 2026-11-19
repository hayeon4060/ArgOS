/**
 * 알림 대응 API — 체크리스트 (설계 자료 API 명세서 C1·C2, 백엔드 gateway/backend .../checklist/ChecklistController)
 * - GET   /checklist-templates?equipmentType=&sensorType= : 기본 항목
 * - GET   /processes/{processId}/workers                 : 받을 공정 담당자
 * - POST  /checklists                                    : 전송 (알림은 ACTING 으로)
 * - GET   /checklists?status=ACTIVE                      : 진행 중 목록 (상단 바 체크리스트 진행)
 * - GET   /checklists/{id}                               : 한 건 (진행 현황 창)
 * - POST  /checklists/{id}/remind                        : 다시 알림 (30초에 한 번)
 * - PATCH /checklists/{id}/assignee                      : 담당자 변경 (명세서 화면설계서는 PATCH /checklists/{id}, 백엔드 코드 기준)
 * - POST  /checklists/{id}/confirm                       : 조치 확인 → 주의 해제
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { checklistView } from './mock/alertData'
import {
  DEFAULT_ITEMS,
  WORKERS,
  activeChecklist,
  confirmChecklist as mockConfirm,
  reassignChecklist as mockReassign,
  remindChecklist as mockRemind,
  scenario,
  sendChecklist as mockSend,
} from './mock/scenario'

/** 기본 체크리스트 항목: { templateId, cause, items: [문장] } */
export function getChecklistTemplate({ equipmentType, sensorType }) {
  if (USE_MOCK) return mockOk({ templateId: 14, cause: '배기팬 과전류', items: DEFAULT_ITEMS })
  return http.get('/checklist-templates', { params: { equipmentType, sensorType } })
}

/** 받을 공정 담당자: [{ id, name, loginId, onDuty, pushReady }] */
export function getProcessWorkers(processId) {
  if (USE_MOCK) return mockOk(WORKERS)
  return http.get(`/processes/${processId}/workers`)
}

/**
 * 체크리스트 전송
 * @param {{ alarmId, templateId, assigneeId, items: string[], sentBy: string }} body sentBy 는 예시 데이터 기록용
 */
export function sendChecklist({ alarmId, templateId, assigneeId, items, sentBy }) {
  if (USE_MOCK) {
    // 예시 데이터는 상도 건조로 알림(1042)만 체크리스트를 보낼 수 있음 ('주의 1건 추가'로 넣은 1043 은 창 확인용)
    if (Number(alarmId) !== 1042) return mockFail({ code: 'MOCK_ONLY', message: '예시 데이터에서는 상도 건조로 알림만 체크리스트를 보낼 수 있습니다.', status: 400 })
    if (activeChecklist()) return mockFail({ code: 'CHECKLIST_EXISTS', message: '이 알림에 이미 진행 중인 체크리스트가 있습니다.', status: 409 })
    const c = mockSend({ items, assigneeId, sentBy })
    return mockOk({ id: c.id, status: 'SENT', push: 'SENT', sentAt: c.sentAt })
  }
  return http.post('/checklists', { alarmId, templateId, assigneeId, items })
}

/** 진행 중 체크리스트 목록 (상단 바) */
export function getActiveChecklists({ factoryId } = {}) {
  if (USE_MOCK) {
    const c = activeChecklist()
    return mockOk(c ? [checklistView(c.id)] : [])
  }
  return http.get('/checklists', { params: { status: 'ACTIVE', ...(factoryId ? { factoryId } : {}) } })
}

/** 체크리스트 한 건 */
export function getChecklist(checklistId) {
  if (USE_MOCK) {
    const data = checklistView(checklistId)
    return data ? mockOk(data) : mockFail({ code: 'NOT_FOUND', message: '없는 체크리스트입니다.', status: 404 })
  }
  return http.get(`/checklists/${checklistId}`)
}

/** 다시 알림: { remindCount, remindAvailableAt } — 30초 안에 또 누르면 429 REMIND_COOLDOWN */
export function remindChecklist(checklistId) {
  if (USE_MOCK) {
    const r = mockRemind()
    if (!r.ok) return mockFail({ code: 'REMIND_COOLDOWN', message: '잠시 뒤에 다시 보낼 수 있습니다.', status: 429 })
    return mockOk({ remindCount: r.remindCount, remindAvailableAt: r.availableAt })
  }
  return http.post(`/checklists/${checklistId}/remind`)
}

/** 담당자 변경 */
export function reassignChecklist(checklistId, { assigneeId, reason }) {
  if (USE_MOCK) {
    mockReassign(assigneeId, reason)
    return mockOk({ id: Number(checklistId), assignee: WORKERS.find((w) => w.id === assigneeId) })
  }
  return http.patch(`/checklists/${checklistId}/assignee`, { assigneeId, reason })
}

/**
 * 조치 확인
 * @param {{ result: 'RESOLVED'|'PARTS_PLANNED'|'NEED_RECHECK', memo: string }} body
 */
export function confirmChecklist(checklistId, { result, memo }) {
  if (USE_MOCK) {
    if (scenario.checklist?.status !== 'REPORTED') {
      return mockFail({ code: 'CHECKLIST_NOT_REPORTED', message: '담당자가 아직 완료 보고를 하지 않았습니다.', status: 409 })
    }
    mockConfirm({ result, memo })
    return mockOk({ checklist: { id: Number(checklistId), status: 'CONFIRMED' }, alarm: { id: scenario.alarm.id, status: 'CLOSED' } })
  }
  return http.post(`/checklists/${checklistId}/confirm`, { result, memo })
}
