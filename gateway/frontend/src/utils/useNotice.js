/**
 * 짧은 안내 문구 상태 (AppNotice 와 같이 사용)
 * - show(문구) 하면 정해진 시간 뒤 자동으로 지워짐
 * - 화면을 떠나면 타이머 정리
 */
import { onBeforeUnmount, ref } from 'vue'

export function useNotice(duration = 3000) {
  const message = ref('')
  let timer = null

  function show(text) {
    message.value = text
    clearTimeout(timer)
    timer = setTimeout(() => (message.value = ''), duration)
  }

  onBeforeUnmount(() => clearTimeout(timer))

  return { message, show }
}
