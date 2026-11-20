/**
 * 채팅 상태 (Pinia) — 상단 바 채팅 숫자와 오른쪽 채팅 패널이 같이 씀 (화면설계서 I2)
 * - 대화방 목록 · 안 읽은 수: 처음 + 새 글이 올 때마다(live — 실제는 SSE chat.message, 예시는 emitLive) 다시 받음
 * - 패널: 열고 닫기, 고른 방, 그 방 글, 입력하던 글(닫아도 남겨 둠)
 * - 보내기: 흐린 말풍선을 먼저 띄우고(보내는 중) → 성공하면 진해짐, 실패하면 '다시 보내기'
 * - 패널이 열려 있고 그 방을 보고 있으면 읽음 처리 (POST …/read)
 * - 공정 담당자는 채팅 없음 (웹에 안 들어옴)
 */
import { defineStore } from 'pinia'
import { computed, reactive, ref, watch } from 'vue'
import { getChatMessages, getChatRooms, markChatRead, sendChatMessage } from '@/api/chat'
import { live } from '@/api/live'
import { useAuthStore } from './auth'
import { ROLE } from '@/types/codes'

export const useChatStore = defineStore('chat', () => {
  const auth = useAuthStore()

  /** 채팅을 쓰는 사람 (본사 관리자 · 공장 담당자) */
  const enabled = computed(() => auth.isLoggedIn && [ROLE.HQ_ADMIN, ROLE.PLANT_ADMIN].includes(auth.role))

  const rooms = ref([])
  const open = ref(false)
  const roomId = ref(null)
  /** 고른 방의 글 { items, hasMore, loading } */
  const thread = reactive({ items: [], hasMore: false, loading: false })
  /** 입력하던 글 (패널을 닫아도 남김 — 화면설계서) */
  const draft = ref('')

  const unread = computed(() => rooms.value.reduce((s, r) => s + (r.unreadCount ?? 0), 0))
  const room = computed(() => rooms.value.find((r) => r.roomId === roomId.value) ?? null)

  async function refreshRooms() {
    if (!enabled.value) return
    try {
      rooms.value = await getChatRooms()
      if (!roomId.value && rooms.value[0]) roomId.value = rooms.value[0].roomId
    } catch {
      // 못 받아도 그대로 (다음 갱신 때 다시)
    }
  }

  /**
   * 고른 방 글 불러오기
   * - older: 위로 '이전 메시지' (before = 지금 맨 앞 글)
   * - 아니면 최근 30개를 받아 지금 목록과 합침 (보내는 중 · 실패한 내 글은 그대로 둠)
   */
  async function loadMessages({ older = false } = {}) {
    const id = roomId.value
    if (!id) return
    thread.loading = true
    try {
      const firstId = thread.items.find((m) => !m.temp)?.id
      const r = await getChatMessages(id, older && firstId ? { before: firstId } : {})
      if (id !== roomId.value) return // 그사이 다른 방을 골랐으면 버림
      const saved = new Map(thread.items.filter((m) => !m.temp).map((m) => [m.id, m]))
      for (const m of r.items) saved.set(m.id, m)
      const temps = thread.items.filter((m) => m.temp)
      thread.items = [...[...saved.values()].sort((a, b) => a.id - b.id), ...temps]
      if (older || !firstId) thread.hasMore = r.hasMore
      if (open.value) readNow()
    } catch {
      // 다음 갱신 때 다시
    } finally {
      thread.loading = false
    }
  }

  /** 지금 보는 방의 마지막 글까지 읽음 */
  async function readNow() {
    const r = room.value
    const last = [...thread.items].reverse().find((m) => !m.temp)
    if (!r || !last || !r.unreadCount) return
    try {
      const { unreadCount } = await markChatRead(r.roomId, last.id)
      r.unreadCount = unreadCount
    } catch {
      // 읽음 실패는 조용히 (숫자만 남음)
    }
  }

  function selectRoom(id) {
    if (id === roomId.value) return
    roomId.value = id
    thread.items = []
    thread.hasMore = false
    loadMessages()
  }

  function toggle() {
    open.value ? close() : show()
  }
  function show() {
    open.value = true
    loadMessages()
  }
  function close() {
    open.value = false
  }

  /* ───────── 보내기 ───────── */
  let tempSeq = 0

  /**
   * @param {string} body
   * @param {object|null} alarm 붙일 알림 (목록 줄) — 카드로 먼저 보여 주려고 통째로 받음
   */
  async function send(body, alarm = null) {
    const text = body.trim()
    if (!text || !roomId.value) return
    const temp = reactive({
      id: `t${++tempSeq}`,
      temp: true,
      pending: true,
      failed: false,
      mine: true,
      body: text,
      alarm: alarm ? { id: alarm.id, title: alarm.title, equipmentName: alarm.location?.split(' · ').at(-1), level: alarm.level } : null,
      sentAt: new Date().toISOString(),
      roomId: roomId.value,
    })
    thread.items.push(temp)
    await deliver(temp)
  }

  async function deliver(temp) {
    temp.pending = true
    temp.failed = false
    try {
      const r = await sendChatMessage(temp.roomId, { body: temp.body, alarmId: temp.alarm?.id ?? null })
      // 진짜 번호로 바꿈 → 다음 불러오기 때 서버 글과 같은 번호로 합쳐짐 (두 번 보이지 않음)
      Object.assign(temp, { id: r.id, sentAt: r.sentAt, temp: false, pending: false })
    } catch {
      temp.pending = false
      temp.failed = true
    }
  }

  /** 실패한 글 다시 보내기 */
  const retry = (m) => deliver(m)

  // 새 글 · 데이터 변화 (live): 방 목록 숫자, 열려 있으면 그 방 글
  watch(
    () => live.revision,
    () => {
      refreshRooms()
      if (open.value) loadMessages()
    },
  )
  // 로그인 · 로그아웃: 처음부터
  watch(
    () => auth.user?.id,
    () => {
      rooms.value = []
      roomId.value = null
      thread.items = []
      open.value = false
      draft.value = ''
      refreshRooms()
    },
    { immediate: true },
  )

  return { enabled, rooms, open, roomId, room, thread, draft, unread, refreshRooms, loadMessages, selectRoom, toggle, show, close, send, retry }
})
