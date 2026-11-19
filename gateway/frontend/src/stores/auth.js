/**
 * 로그인 상태 스토어 (Pinia)
 * - 액세스 토큰, 로그인 사용자 정보, 로그인 유지 만료 시각을 보관
 * - 새로고침해도 유지되도록 utils/storage.js 로 저장·복원
 * - 리프레시 토큰은 쓰지 않음 (토큰 자체 13시간 유효, A안 확정 2026-11-16)
 * - 로그인 유지 시간(13시간)·연장(1시간)은 features/session/sessionConfig.js
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { SESSION_MS } from '@/features/session/sessionConfig'
import { clearAuth, loadAuth, saveAuth } from '@/utils/storage'

export const useAuthStore = defineStore('auth', () => {
  // 저장된 로그인 정보가 있으면 복원
  const saved = loadAuth()

  const accessToken = ref(saved?.accessToken ?? null)
  /** 로그인 사용자: { id, loginId, name, role, scope: { siteId, factoryIds, processIds } } */
  const user = ref(saved?.user ?? null)
  /** 로그인 유지 만료 시각 (ms). 예전에 저장된 값에 없으면 0 → 바로 만료 처리 */
  const expiresAt = ref(saved?.expiresAt ?? 0)

  const isLoggedIn = computed(() => !!accessToken.value)
  const role = computed(() => user.value?.role ?? null)

  /** 만료 시각이 지났는지 (지금 시각 기준) */
  function isExpired() {
    return isLoggedIn.value && Date.now() >= expiresAt.value
  }

  function persist() {
    saveAuth({ accessToken: accessToken.value, user: user.value, expiresAt: expiresAt.value })
  }

  /** 아이디·비밀번호 로그인: 성공하면 상태와 브라우저에 저장 */
  async function login(loginId, password, client = 'WEB') {
    const result = await authApi.login({ loginId, password, client })
    accessToken.value = result.accessToken
    user.value = result.user
    // 서버가 만료 시각을 주면 그 값, 없으면 지금부터 13시간
    expiresAt.value = result.sessionExpiresAt ? Date.parse(result.sessionExpiresAt) : Date.now() + SESSION_MS
    persist()
  }

  /** 로그인 유지 연장: 서버가 돌려준 새 만료 시각으로 바꿈 */
  async function extend() {
    const result = await authApi.extendSession(expiresAt.value)
    expiresAt.value = Date.parse(result.sessionExpiresAt)
    persist()
  }

  /** 로그아웃: 서버 요청은 실패해도 화면 쪽 로그인 정보는 반드시 삭제 */
  async function logout({ callServer = true } = {}) {
    if (callServer && accessToken.value) {
      try {
        await authApi.logout()
      } catch {
        // 서버 로그아웃 실패는 무시
      }
    }
    accessToken.value = null
    user.value = null
    expiresAt.value = 0
    clearAuth()
  }

  return { accessToken, user, expiresAt, isLoggedIn, role, isExpired, login, extend, logout }
})
