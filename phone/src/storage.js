/**
 * 담당자 앱 로그인 정보 브라우저 저장 — 관제 웹 src/utils/storage.js 대신 쓰임 (vite.config.js 별칭)
 * - 내보내는 함수 이름·모양은 웹 것과 같고, 저장 이름만 argos.phone.* (웹은 argos.*)
 *   → 같은 브라우저에서 웹 로그인과 앱 로그인이 서로 덮어쓰지 않음
 * - 웹 예시 상황(scenario.js PHONE_AUTH_KEY)이 'argos.phone.auth' 를 보고, 받는 담당자가 앱에 있으면 자동 진행을 안 함
 */
const KEY = 'argos.phone.auth'
const SAVED_ID_KEY = 'argos.phone.savedLoginId'

export function loadAuth() {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function saveAuth(auth) {
  try {
    localStorage.setItem(KEY, JSON.stringify(auth))
  } catch {
    // 저장 실패 시 무시 (새로고침하면 다시 로그인)
  }
}

export function loadSavedLoginId() {
  try {
    return localStorage.getItem(SAVED_ID_KEY) ?? ''
  } catch {
    return ''
  }
}

export function saveLoginId(loginId) {
  try {
    if (loginId) localStorage.setItem(SAVED_ID_KEY, loginId)
    else localStorage.removeItem(SAVED_ID_KEY)
  } catch {
    // 저장 실패는 무시
  }
}

export function clearAuth() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 무시
  }
}
