/**
 * 담당자 앱 체크리스트 API (API 명세서 M2, 백엔드 gateway/backend ChecklistController '앱' 부분)
 * - GET   /me/checklists?status=ACTIVE|DONE   : 내 체크리스트
 * - PATCH /checklists/{id}/items/{itemId}     : 항목 체크·풀기 + 결과·메모 (첫 체크면 IN_PROGRESS)
 * - POST  /checklists/{id}/report             : 완료 보고 → 관제 웹 '완료 · 확인 필요'
 * 한 건 보기(GET /checklists/{id})는 웹과 같은 '@/api/checklists' getChecklist 를 씀
 *
 * 예시 데이터 모드: 웹 예시 상황(scenario.js)의 체크리스트를 직접 바꿈 → 웹 진행 창에 바로 보임
 * - 웹 예시 상황은 끝낸 항목을 차례대로 쌓음(doneTimes) → 앱도 앞 항목부터 차례로 체크, 마지막 항목부터 풀기
 *   (실제 서버는 순서 상관없음)
 * - 항목별 결과·메모는 checklist.results[i], 완료 보고 한 줄은 checklist.reportMemo (웹 alertData.checklistView 가 읽음)
 * - 검사·오류 문구는 백엔드 ChecklistService 와 같게
 */
import http from '@/api/http'
import { emitLive } from '@/api/live'
import { USE_MOCK, mockFail, mockOk } from '@/api/mock'
import { checklistView } from '@/api/mock/alertData'
import { activeChecklist, scenario, scenarioNow, scenarioSynced } from '@/api/mock/scenario'

/** 예시 데이터 항목 id = 501, 502 … (웹 alertData.checklistView 와 같게) */
const ITEM_ID_BASE = 500

/** 예시 데이터: 앱에서 고칠 수 있는 내 체크리스트인지 */
function mockEditError(checklistId, userId) {
  const c = scenario.checklist
  if (!c || c.id !== Number(checklistId)) return { code: 'CHECKLIST_NOT_FOUND', message: '없는 체크리스트입니다.', status: 404 }
  if (c.assigneeId !== userId) return { code: 'NOT_ASSIGNEE', message: '담당자가 바뀌어 더 이상 내 체크리스트가 아닙니다.', status: 403 }
  if (c.status !== 'SENT' && c.status !== 'IN_PROGRESS') {
    return { code: 'CHECKLIST_CLOSED', message: '이미 완료 보고한 체크리스트입니다.', status: 409 }
  }
  return null
}

/**
 * 예시 데이터: 이 담당자가 앱을 켜 두었는지 예시 상황에 표시 (scenario.phoneUsers)
 * - 켜 두었으면 웹에서 체크리스트를 보내도 자동 진행 흉내를 하지 않음 (사람이 앱에서 체크)
 * - 실제 서버에서는 푸시 토큰 등록(POST /me/push-token)이 비슷한 역할
 */
export function markPhoneUser(userId, online) {
  if (!USE_MOCK) return
  scenarioSynced.then(() => {
    const ids = new Set(scenario.phoneUsers ?? [])
    if (online) ids.add(userId)
    else ids.delete(userId)
    scenario.phoneUsers = [...ids]
  })
}

/**
 * 내 체크리스트: [{ id, status, level, title, sentAt, doneCount, totalCount }]
 * @param {{ status?: 'ACTIVE'|'DONE', userId: number }} query userId 는 예시 데이터용 (서버는 토큰으로 앎)
 */
export function getMyChecklists({ status = 'ACTIVE', userId }) {
  if (USE_MOCK) {
    const c = scenario.checklist
    const statuses = status === 'DONE' ? ['REPORTED', 'CONFIRMED'] : ['SENT', 'IN_PROGRESS']
    if (!c || c.assigneeId !== userId || !statuses.includes(c.status)) return mockOk([])
    const v = checklistView(c.id)
    const title = `${v.equipmentName} 전류 ${v.valueAtAlarm} ${v.unit}` // 백엔드: 설비 + 센서 종류 + 값
    return mockOk([
      { id: v.id, status: v.status, level: v.level, title, sentAt: v.sentAt, doneCount: c.doneTimes.length, totalCount: c.items.length },
    ])
  }
  return http.get('/me/checklists', { params: { status } })
}

/**
 * 항목 체크·풀기 (이미 체크한 항목을 done: true 로 다시 보내면 결과·메모만 고침)
 * @param {{ done: boolean, result?: string, memo?: string, userId: number }} body userId 는 예시 데이터용
 * @returns {Promise<{ itemId, doneAt, doneCount, totalCount }>}
 */
export function checkChecklistItem(checklistId, itemId, { done, result, memo, userId }) {
  if (USE_MOCK) {
    const error = mockEditError(checklistId, userId)
    if (error) return mockFail(error)
    const c = activeChecklist()
    const index = itemId - ITEM_ID_BASE - 1
    const count = c.doneTimes.length
    if (!c.items[index]) return mockFail({ code: 'ITEM_NOT_FOUND', message: '없는 항목입니다.', status: 404 })
    if (done && index > count) return mockFail({ code: 'ITEM_ORDER', message: '앞 항목부터 차례로 체크해 주세요.', status: 409 })
    if (!done && index !== count - 1) {
      return mockFail({ code: 'ITEM_ORDER', message: '마지막으로 체크한 항목부터 풀 수 있습니다.', status: 409 })
    }

    c.results ??= [] // 처음 상황·경보 상황의 체크리스트에는 없음
    if (done) {
      if (index === count) c.doneTimes.push(scenarioNow())
      c.results[index] = { result, memo: memo || null }
    } else {
      c.doneTimes.pop()
      c.results.splice(index)
      if (index === 1) c.memo = null // 처음 상황의 2번째 항목 현장 메모도 같이 지움
    }
    if (c.status === 'SENT') c.status = 'IN_PROGRESS'
    emitLive('checklist.updated')
    return mockOk({ itemId, doneAt: checklistView(c.id).items[index].doneAt, doneCount: c.doneTimes.length, totalCount: c.items.length })
  }
  return http.patch(`/checklists/${checklistId}/items/${itemId}`, { done, result, memo })
}

/**
 * 완료 보고 (모든 항목을 체크한 뒤)
 * @param {{ memo?: string, userId: number }} body userId 는 예시 데이터용
 * @returns {Promise<{ id, status: 'REPORTED', reportedAt }>}
 */
export function reportChecklist(checklistId, { memo, userId }) {
  if (USE_MOCK) {
    const error = mockEditError(checklistId, userId)
    if (error) return mockFail(error)
    const c = activeChecklist()
    if (c.doneTimes.length < c.items.length) {
      return mockFail({ code: 'ITEMS_NOT_DONE', message: '체크하지 않은 항목이 있습니다.', status: 409 })
    }
    c.status = 'REPORTED'
    c.reportedAt = scenarioNow()
    c.reportMemo = memo || null
    emitLive('checklist.updated')
    return mockOk({ id: c.id, status: 'REPORTED', reportedAt: checklistView(c.id).reportedAt })
  }
  return http.post(`/checklists/${checklistId}/report`, { memo })
}
