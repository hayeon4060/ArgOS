/**
 * 채팅 API (API 명세서 I2, 화면설계서 I2 '채팅 패널')
 * - GET  /chat/rooms                       : 대화방 목록 (상대, 마지막 글, 안 읽은 수)
 * - GET  /chat/rooms/{roomId}/messages      : 글 30개씩 (before 로 이전 것)
 * - POST /chat/rooms/{roomId}/messages      : 보내기 { body, alarmId? } → 상대에게는 SSE chat.message
 * - POST /chat/rooms/{roomId}/read          : 읽음 { lastReadMessageId }
 * - 백엔드 chat/ChatController (대화방 = 사업장, roomId = 사업장 id). 공정 담당자는 403 CHAT_NOT_ALLOWED
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { listMessages, listRooms, markRead, sendMessage } from './mock/chatData'
import { useAuthStore } from '@/stores/auth'

const actor = () => useAuthStore().user
const result = (r) => (r.error ? mockFail(r.error) : mockOk(r.data))

/** [{ roomId, siteName, peer: { id, name, role, online }, lastMessage: { body, sentAt }, unreadCount }] */
export function getChatRooms() {
  if (USE_MOCK) return mockOk(listRooms(actor()))
  return http.get('/chat/rooms')
}

/** { items: [{ id, senderId, senderName, mine, body, alarm, sentAt, read }], hasMore } (오래된 것 → 최근 순서로 씀) */
export function getChatMessages(roomId, { before = null, size = 30 } = {}) {
  if (USE_MOCK) return result(listMessages(actor(), roomId, { before, size }))
  return http.get(`/chat/rooms/${roomId}/messages`, { params: before ? { before, size } : { size } })
}

/** → { id, sentAt } */
export function sendChatMessage(roomId, { body, alarmId = null }) {
  if (USE_MOCK) return result(sendMessage(actor(), roomId, { body, alarmId }))
  return http.post(`/chat/rooms/${roomId}/messages`, { body, alarmId })
}

/** → { unreadCount } */
export function markChatRead(roomId, lastReadMessageId) {
  if (USE_MOCK) return result(markRead(actor(), roomId, lastReadMessageId))
  return http.post(`/chat/rooms/${roomId}/read`, { lastReadMessageId })
}
