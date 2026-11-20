/**
 * 로그인 정보 브라우저 저장
 * - 새로고침해도 로그인이 유지되도록 localStorage 에 저장
 * - 토큰 저장 위치(localStorage / 쿠키)는 백엔드 인증 방식이 확정되면 이 파일만 바꾸면 됨
 */
const KEY = 'argos.auth'

/** 저장된 로그인 정보 읽기 (없거나 깨져 있으면 null) */
export function loadAuth() {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 로그인 정보 저장 */
export function saveAuth(auth) {
  try {
    localStorage.setItem(KEY, JSON.stringify(auth))
  } catch {
    // 사생활 보호 모드 등에서 저장 실패 시 무시 (새로고침하면 다시 로그인)
  }
}

const SAVED_ID_KEY = 'argos.savedLoginId'

/** 로그인 화면 '아이디 저장': 저장된 아이디 읽기 (없으면 빈 문자열) */
export function loadSavedLoginId() {
  try {
    return localStorage.getItem(SAVED_ID_KEY) ?? ''
  } catch {
    return ''
  }
}

/** '아이디 저장' 체크 시 저장, 해제 시 삭제 */
export function saveLoginId(loginId) {
  try {
    if (loginId) localStorage.setItem(SAVED_ID_KEY, loginId)
    else localStorage.removeItem(SAVED_ID_KEY)
  } catch {
    // 저장 실패는 무시
  }
}

/** 로그인 정보 삭제 */
export function clearAuth() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 무시
  }
}
