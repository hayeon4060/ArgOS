/**
 * '데이터가 바뀌었다' 신호 (실시간 갱신 자리)
 * - 화면은 live.revision 이 바뀌면 자기 데이터를 조용히 다시 불러옴 (utils/useLiveReload.js)
 * - 예시 데이터 모드: 상황 바꾸기 도구·주의 확인·체크리스트 전송 등 예시 상황이 바뀔 때 emitLive 를 부름
 *   (api/mock/scenario.js)
 * - 실제 서버: 로그인하면 GET /stream (SSE) 을 열고, 아래 이벤트가 오면 emitLive(이벤트 이름)
 *   alarm.created · alarm.updated · checklist.updated · level.changed · threshold.changed · command.result · reset
 *   fire.started · fire.updated · fire.cleared
 *   + chat.message (명세서 I2, 백엔드에는 아직 없음)
 *   (백엔드 gateway/backend .../stream/StreamHub 와 같은 이름)
 * - 브라우저 EventSource 는 Authorization 헤더를 못 붙이므로 fetch 로 스트림을 직접 읽음
 */
import { reactive } from 'vue'

export const live = reactive({
  /** 바뀔 때마다 1씩 커지는 번호 (화면은 이 값만 지켜봄) */
  revision: 0,
  /** 마지막 이벤트 이름 (예: 'alarm.created') — 디버깅·강조 표시용 */
  lastEvent: '',
  /** 실시간 연결 중인지 (상단 바 '실시간 수신 중' 표시) */
  connected: false,
})

/** 데이터가 바뀌었음을 알림 */
export function emitLive(event) {
  live.lastEvent = event
  live.revision += 1
}

// 화면을 다시 불러올 이벤트 (ping, sensor.value 는 너무 잦아서 제외)
const RELOAD_EVENTS = new Set([
  'alarm.created',
  'alarm.updated',
  'checklist.updated',
  'level.changed',
  'threshold.changed',
  'command.result',
  'fire.started', // 화재 감지 · 방송 · 해제 (백엔드 FireService) — 2026-11-19 전수조사에서 빠져 있던 것 추가
  'fire.updated',
  'fire.cleared',
  'equipment.updated', // 설비 설정(사용 상태 · 배기팬 자동/수동)이 바뀜 — 2026-11-19 추가, 백엔드 StreamHub 에 추가 필요
  'chat.message', // 채팅 새 글 (상단 바 숫자 · 채팅 패널) — 백엔드 StreamHub 에는 아직 없음 (명세서 I2)
  'reset',
])

const STREAM_URL = `${import.meta.env.VITE_WS_BASE_URL || import.meta.env.VITE_API_BASE_URL || '/api/v1'}/stream`

let controller = null
let retryTimer = null

/**
 * 실시간 연결 시작 (로그인 뒤). 끊기면 3초 뒤 다시 연결
 * @param {() => string|null} getToken 지금 액세스 토큰
 */
export function startLive(getToken) {
  stopLive()
  const token = getToken()
  if (!token) return
  const ctrl = new AbortController()
  controller = ctrl
  readStream(token, ctrl.signal)
    .catch(() => {})
    .finally(() => {
      live.connected = false
      if (controller === ctrl && !ctrl.signal.aborted) {
        retryTimer = setTimeout(() => startLive(getToken), 3000)
      }
    })
}

/** 실시간 연결 끊기 (로그아웃) */
export function stopLive() {
  clearTimeout(retryTimer)
  retryTimer = null
  if (controller) controller.abort()
  controller = null
  live.connected = false
}

async function readStream(token, signal) {
  const res = await fetch(STREAM_URL, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    signal,
  })
  if (!res.ok || !res.body) throw new Error(`stream ${res.status}`)
  live.connected = true
  // 다시 연결됐으면 그동안 놓친 변화가 있을 수 있으므로 한 번 다시 불러옴
  emitLive('connected')

  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) return
    buffer += value.replace(/\r\n/g, '\n')
    let cut
    // SSE 이벤트는 빈 줄로 나뉨
    while ((cut = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, cut)
      buffer = buffer.slice(cut + 2)
      const name = block
        .split('\n')
        .find((line) => line.startsWith('event:'))
        ?.slice(6)
        .trim()
      if (name && RELOAD_EVENTS.has(name)) emitLive(name)
    }
  }
}
