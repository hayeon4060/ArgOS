/**
 * 인증 API (설계 자료 API 명세서 A1 기준 + 로그인 유지 13시간 변경)
 * - mock 모드에서는 예시 계정(api/mock/data.js)으로 로그인
 * - 리프레시 토큰은 쓰지 않음: 토큰 자체를 13시간 유효로 하는 A안 확정(2026-11-16 재원님)
 * - 로그인 유지: 로그인 응답의 sessionExpiresAt(만료 시각)까지, '연장'하면 POST /auth/extend
 *   (명세서에는 없는 값·API → 백엔드와 합의 전 가정, docs/backend-notes.md 참고)
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { MOCK_PASSWORD, MOCK_USERS } from './mock/data'
import { EXTEND_MS, SESSION_MS } from '@/features/session/sessionConfig'
import { ROLE } from '@/types/codes'

/**
 * 로그인
 * @param {{ loginId: string, password: string, client?: 'WEB'|'APP' }} payload  client: 웹 / 담당자 앱
 * @returns {Promise<{ accessToken, expiresIn, sessionExpiresAt, pwResetRequired, firstPage, user }>}
 *   sessionExpiresAt: 로그인 유지 만료 시각 (ISO 문자열, 로그인 + 13시간)
 *   user: { id, loginId, name, role, scope: { siteId, factoryIds, processIds } }
 */
export function login({ loginId, password, client = 'WEB' }) {
  if (USE_MOCK) {
    const found = MOCK_USERS.find((u) => u.loginId === loginId)
    if (!found || password !== MOCK_PASSWORD) {
      return mockFail({ code: 'AUTH_INVALID', message: '아이디 또는 비밀번호가 맞지 않습니다', status: 401 })
    }
    // 공정 담당자는 앱, 관리자는 웹으로만 (서버와 같은 검사)
    if ((found.role === ROLE.WORKER) !== (client === 'APP')) {
      const message = client === 'APP' ? '관리자 계정은 PC 웹에서 로그인해 주세요' : '공정 담당자는 담당자 앱으로 로그인해 주세요'
      return mockFail({ code: 'CLIENT_NOT_ALLOWED', message, status: 403 })
    }
    return mockOk({
      accessToken: `mock-access-${found.id}`,
      expiresIn: 1800,
      sessionExpiresAt: new Date(Date.now() + SESSION_MS).toISOString(),
      pwResetRequired: false,
      firstPage: found.role === ROLE.HQ_ADMIN ? 'SITE_SELECT' : 'FACTORY_DASHBOARD',
      user: found,
    })
  }
  // client: 웹(WEB) / 앱(APP) 구분 — 서버가 권한별 접속 기기를 검사함
  return http.post('/auth/login', { loginId, password, client })
}

/**
 * 로그인 유지 연장 (남은 시간에 1시간 더함)
 * @param {number} currentExpiresAt 지금 만료 시각(ms) — 예시 데이터 모드에서만 사용 (실제로는 서버가 계산)
 * @returns {Promise<{ sessionExpiresAt: string }>}
 */
export function extendSession(currentExpiresAt) {
  if (USE_MOCK) {
    return mockOk({ sessionExpiresAt: new Date(currentExpiresAt + EXTEND_MS).toISOString() })
  }
  return http.post('/auth/extend')
}

/** 로그아웃 (서버에서 토큰을 무효로 처리) */
export function logout() {
  if (USE_MOCK) return mockOk(null)
  return http.post('/auth/logout')
}
