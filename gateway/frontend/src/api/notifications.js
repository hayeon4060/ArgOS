/**
 * 상단 바 숫자 API (설계 자료 API 명세서 I1 '상단 바')
 * - GET /notifications/summary : 알림 벨·채팅 아이콘·체크리스트 진행 표시에 쓰는 숫자
 * - 화면은 unackedAlarmCount 만 씀 (채팅 숫자는 stores/chat.js 대화방 합계, 화재는 알림 목록 · GET /fires)
 * - 서버는 unreadChatCount 0 · activeFire null 고정 (TODO(백엔드): docs/api/frontend-api-audit-1120.md 2장)
 */
import http from './http'
import { USE_MOCK, mockOk } from './mock'
import { hasActiveChecklist, mockAlarms } from './mock/data'
import { scenario } from './mock/scenario'
import { unreadTotal } from './mock/chatData'
import { useAuthStore } from '@/stores/auth'

/**
 * @returns {Promise<{ unackedAlarmCount: number, activeChecklistCount: number, unreadChatCount: number, activeFire: object|null }>}
 */
export function getNotificationSummary() {
  if (USE_MOCK) {
    return mockOk({
      // 예시 알림 중 아직 조치가 끝나지 않은 것 (지금 예시 상황: 5공장 CT-17 주의가 진행 중이면 1건)
      unackedAlarmCount: mockAlarms().filter((a) => a.status !== 'CLOSED').length,
      activeChecklistCount: hasActiveChecklist() ? 1 : 0,
      unreadChatCount: unreadTotal(useAuthStore().user), // 채팅 예시 데이터의 안 읽은 글 (api/mock/chatData.js)
      // 진행 중 화재 (명세서: { id, location } 정도. 예시 상황에서 화재면 채움)
      activeFire: scenario.fire?.status === 'ACTIVE' ? { id: scenario.fire.id, location: '울산공장 · 5공장 · 도장 공정 · 상도 건조로 버너실' } : null,
    })
  }
  return http.get('/notifications/summary')
}
