/**
 * 로그인 유지 시간 타이머 (상단 바 SessionTimer 에서 사용)
 * - 1초마다 남은 시간을 계산
 * - 만료 10분 전에 한 번 onWarn 호출 (연장하면 다시 한 번 알릴 수 있게 초기화)
 * - 만료되면 로그아웃하고 로그인 화면으로 (안내 문구용 reason=expired)
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { WARN_BEFORE_MS } from './sessionConfig'
import { useAuthStore } from '@/stores/auth'

export function useSessionTimer({ onWarn }) {
  const auth = useAuthStore()
  const router = useRouter()

  const now = ref(Date.now())
  const timer = setInterval(tick, 1000)
  onBeforeUnmount(() => clearInterval(timer))

  /** 남은 시간 (ms, 0 아래로는 안 내려감) */
  const remainingMs = computed(() => Math.max(0, auth.expiresAt - now.value))

  /** 만료가 가까운지 (안내 시점 이하) — 화면에서 글자색을 바꿀 때 사용 */
  const isNearExpiry = computed(() => remainingMs.value <= WARN_BEFORE_MS)

  // 이번 만료 시각에 대해 이미 알렸는지 (연장해서 만료 시각이 바뀌면 다시 false)
  let warned = false
  watch(
    () => auth.expiresAt,
    () => (warned = false),
  )

  async function tick() {
    now.value = Date.now()
    if (!auth.isLoggedIn) return

    if (remainingMs.value <= 0) {
      clearInterval(timer)
      await auth.logout()
      router.replace({ name: 'login', query: { reason: 'expired' } })
      return
    }

    if (!warned && isNearExpiry.value) {
      warned = true
      onWarn?.()
    }
  }

  return { remainingMs, isNearExpiry }
}
