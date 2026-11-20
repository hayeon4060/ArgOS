/**
 * 화면 아래 알림 띠 (화면설계안 js/core.js toast(), 화면설계서 R2 --m-toast)
 * - 어느 화면에서든 toast('보냈습니다') 로 띄움. 화면은 components/AppToasts.vue 한 곳에서 그림
 * - type: ok(초록) / warn(주황) / err(빨강) / info(파랑)
 * - action: 오른쪽 파란 글자 단추 (예: '확인하기' → 체크리스트 진행 창)
 * - ms: 보여 주는 시간 (기본 3.4초, 0 이면 직접 닫을 때까지)
 * - 짧은 안내만 필요한 기존 화면은 AppNotice(useNotice)를 그대로 씀
 */
import { reactive } from 'vue'

export const toasts = reactive([])

let nextId = 1

/** @returns {() => void} 닫기 함수 */
export function toast(message, { type = 'ok', action = '', onAction = null, ms = 3400 } = {}) {
  // 같은 문구가 이미 떠 있으면 바꿔 띄움
  const same = toasts.findIndex((t) => t.message === message)
  if (same >= 0) toasts.splice(same, 1)

  const item = { id: nextId++, message, type, action, onAction }
  toasts.push(item)
  const close = () => {
    const i = toasts.findIndex((t) => t.id === item.id)
    if (i >= 0) toasts.splice(i, 1)
  }
  if (ms) setTimeout(close, ms)
  return close
}

/** 모두 닫기 (주의가 해제됐을 때 남은 안내 정리) */
export function clearToasts() {
  toasts.splice(0)
}
