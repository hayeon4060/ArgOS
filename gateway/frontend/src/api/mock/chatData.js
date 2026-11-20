/**
 * 채팅 예시 데이터 (화면설계안 rev-shell.js chatRooms() · chatMsgs(), API 명세서 I2 /chat/*)
 *
 * - 백엔드에는 채팅 API 가 없음 (notifications/summary 의 unreadChatCount 도 0 고정) → 명세서 I2 모양
 * - 대화방 = 사업장 하나 (본사 관리자 ↔ 그 사업장 공장 담당자). 본사 관리자는 3곳, 공장 담당자는 자기 사업장 1곳
 *   → 같은 방을 두 계정이 같이 봄: 본사에서 보낸 글이 공장 담당자 화면에도 보임
 * - 읽음: 방마다 '본사 쪽' · '공장 쪽'이 마지막으로 읽은 글 번호를 기억
 * - 보내면 1.6초 뒤 상대가 짧게 답함 (설계안 시연 동작 — 예시 데이터에서만)
 * - 새 글이 생기면 emitLive('chat.message') → 실제 서버의 SSE chat.message 와 같은 흐름 (api/live.js)
 * - 상태는 이 파일 안에만 있음 (새로고침하면 처음으로)
 */
import { emitLive } from '../live'
import { mockAlarms } from './data'

const pad = (n) => String(n).padStart(2, '0')
const iso = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}+09:00`

/** 지금부터 n분 전 (대화 순서가 늘 시간 순서가 되게) */
const ago = (min) => iso(new Date(Date.now() - min * 60_000))

/** 본사 관리자 (공장 담당자 화면의 상대) */
const HQ = { id: 1, name: '서지훈', role: 'HQ_ADMIN', online: true }

/** 방 = 사업장 (설계안 chatRooms: 울산 · 아산 · 전주) */
const ROOMS = [
  { roomId: 1, siteId: 1, siteName: '울산공장', peer: { id: 7, name: '한지우', role: 'PLANT_ADMIN', online: true } },
  { roomId: 2, siteId: 2, siteName: '아산공장', peer: { id: 101, name: '이수아', role: 'PLANT_ADMIN', online: false } },
  { roomId: 3, siteId: 3, siteName: '전주공장', peer: { id: 102, name: '박건우', role: 'PLANT_ADMIN', online: true } },
]

/* ───────── 처음 대화 (설계안 chatMsgs) ───────── */
let nextId = 5301
const msg = (side, body, sentAt, alarm = null) => ({ id: nextId++, side, body, alarm, sentAt })
const MESSAGES = {
  1: [
    msg('HQ', '5공장 도장 공정 오전 점검 결과 공유 부탁드립니다.', ago(340)),
    msg('SITE', '환기·배기설비 점검일지 등록했습니다. 이상 없습니다.', ago(332)),
    msg('HQ', '확인했습니다. 상도 건조로 전류 기준 조정 건은 이력에 남겨 주세요.', ago(268)),
    msg('SITE', '네, 기준값 변경 사유에 적어 두었습니다.', ago(261)),
    msg('HQ', '오후에 배기팬 벨트 교체 일정도 알려 주세요.', ago(46)),
    msg('SITE', '벨트 교체는 내일 08:30 예정입니다.', ago(22)),
    msg('SITE', '교체 후 전류 값 다시 공유드리겠습니다.', ago(21)),
  ],
  2: [msg('SITE', '야간조 인수인계 사항 정리해 두었습니다.', ago(60 * 30)), msg('HQ', '확인했습니다.', ago(60 * 30 - 6))],
  3: [msg('SITE', '차체 라인 정기 점검 끝났습니다.', ago(60 * 20)), msg('HQ', '수고하셨습니다.', ago(60 * 20 - 6))],
}
/** 방마다 쪽별 마지막으로 읽은 글 번호 (울산: 본사는 2건, 공장은 1건 안 읽음) */
const READ = {
  1: { HQ: MESSAGES[1][4].id, SITE: MESSAGES[1][3].id },
  2: { HQ: MESSAGES[2][1].id, SITE: MESSAGES[2][1].id },
  3: { HQ: MESSAGES[3][1].id, SITE: MESSAGES[3][1].id },
}

/* ───────── 누가 어느 방 ───────── */
const sideOf = (actor) => (actor?.role === 'HQ_ADMIN' ? 'HQ' : 'SITE')
const other = (side) => (side === 'HQ' ? 'SITE' : 'HQ')
/** 볼 수 있는 방: 본사 관리자 전부, 공장 담당자 자기 사업장 (공정 담당자는 없음) */
function roomsOf(actor) {
  if (actor?.role === 'HQ_ADMIN') return ROOMS
  if (actor?.role === 'PLANT_ADMIN') return ROOMS.filter((r) => r.siteId === actor.scope?.siteId)
  return []
}
const roomOf = (actor, roomId) => roomsOf(actor).find((r) => r.roomId === Number(roomId))
const notFound = { code: 'NOT_FOUND', message: '없는 대화방입니다.', status: 404 }
const unreadOf = (side, roomId) => MESSAGES[roomId].filter((m) => m.side !== side && m.id > READ[roomId][side]).length

/** 글 한 개 → 명세서 모양 */
function view(actor, roomId, m) {
  const side = sideOf(actor)
  const room = ROOMS.find((r) => r.roomId === roomId)
  const sender = m.side === 'HQ' ? HQ : room.peer
  return {
    id: m.id,
    senderId: sender.id,
    senderName: sender.name,
    mine: m.side === side,
    body: m.body,
    alarm: m.alarm,
    sentAt: m.sentAt,
    // 내 글이면 상대가 읽었는지
    read: m.side === side ? m.id <= READ[roomId][other(side)] : true,
  }
}

/* ───────── API ───────── */

/** GET /chat/rooms */
export function listRooms(actor) {
  const side = sideOf(actor)
  return roomsOf(actor).map((r) => {
    const last = MESSAGES[r.roomId].at(-1)
    return {
      roomId: r.roomId,
      siteName: r.siteName,
      // 상대: 본사 관리자에게는 그 사업장 공장 담당자, 공장 담당자에게는 본사 관리자
      peer: side === 'HQ' ? { ...r.peer } : { ...HQ },
      lastMessage: last ? { body: last.body, sentAt: last.sentAt } : null,
      unreadCount: unreadOf(side, r.roomId),
    }
  })
}

/** 안 읽은 글 합계 (상단 바 숫자, notifications/summary) */
export const unreadTotal = (actor) => listRooms(actor).reduce((s, r) => s + r.unreadCount, 0)

/** GET /chat/rooms/{id}/messages?before=&size= → { items(오래된 것 → 최근), hasMore } */
export function listMessages(actor, roomId, { before = null, size = 30 } = {}) {
  const room = roomOf(actor, roomId)
  if (!room) return { error: notFound }
  const all = MESSAGES[room.roomId].filter((m) => !before || m.id < Number(before))
  const page = all.slice(-size)
  return { data: { items: page.map((m) => view(actor, room.roomId, m)), hasMore: all.length > page.length } }
}

/** 알림 붙이기 { id, title, equipmentName, level } (level 은 명세서에 없음 — 카드 색) */
function alarmCard(alarmId) {
  const a = mockAlarms().find((x) => x.id === Number(alarmId))
  if (!a) return null
  return { id: a.id, title: a.title, equipmentName: a.location.split(' · ').at(-1), level: a.level, sensorTag: a.sensorTag, value: a.value, unit: a.unit, raisedAt: a.raisedAt }
}

/** POST /chat/rooms/{id}/messages { body, alarmId } → { id, sentAt } */
export function sendMessage(actor, roomId, { body, alarmId = null }) {
  if (actor?.role === 'WORKER') return { error: { code: 'CHAT_NOT_ALLOWED', message: '공정 담당자는 채팅을 쓰지 않습니다.', status: 403 } }
  const room = roomOf(actor, roomId)
  if (!room) return { error: notFound }
  const text = (body ?? '').trim()
  if (!text || text.length > 1000) return { error: { code: 'VALIDATION_FAILED', message: '메시지를 1,000자 안으로 입력해 주세요.', status: 400 } }
  const side = sideOf(actor)
  const m = msg(side, text, iso(new Date()), alarmId ? alarmCard(alarmId) : null)
  MESSAGES[room.roomId].push(m)
  READ[room.roomId][side] = m.id // 내 글은 읽은 것으로
  emitLive('chat.message')
  replyLater(room.roomId, other(side), !!alarmId)
  return { data: { id: m.id, sentAt: m.sentAt } }
}

/** 시연: 상대가 1.6초 뒤 짧게 답함 (설계안 chatPanel send()) */
function replyLater(roomId, side, withAlarm) {
  setTimeout(() => {
    const m = msg(side, withAlarm ? '확인했습니다. 조치 상황 계속 공유해 주세요.' : '네, 확인했습니다.', iso(new Date()))
    MESSAGES[roomId].push(m)
    READ[roomId][side] = m.id // 답한 쪽은 앞 글을 다 읽은 것 → 내 글에 '읽음'
    emitLive('chat.message')
  }, 1600)
}

/** POST /chat/rooms/{id}/read { lastReadMessageId } → { unreadCount } */
export function markRead(actor, roomId, lastReadMessageId) {
  const room = roomOf(actor, roomId)
  if (!room) return { error: notFound }
  const side = sideOf(actor)
  READ[room.roomId][side] = Math.max(READ[room.roomId][side], Number(lastReadMessageId) || 0)
  return { data: { unreadCount: unreadOf(side, room.roomId) } }
}
