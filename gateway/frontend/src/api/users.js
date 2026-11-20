/**
 * 사용자 관리 API (설계 자료 API 명세서 G1 · A1)
 * - GET    /users?role=&factoryId=&active=&q=&page=&size= : 목록 + 권한별 인원(counts)
 * - GET    /users/{id}                                    : 한 명 + 변경 기록
 * - GET    /users/check-login-id?loginId=                 : 아이디 중복 확인
 * - POST   /users                                         : 추가 → 임시 비밀번호(한 번만)
 * - PUT    /users/{id}                                    : 이름·연락처·권한·담당 범위
 * - PATCH  /users/{id}/status                             : 사용 정지 · 다시 사용
 * - DELETE /users/{id}                                    : 삭제 (기록은 남음)
 * - POST   /users/{id}/reset-password                     : 비밀번호 초기화 → 임시 비밀번호
 * - GET    /me · PUT /me/password                         : 내 정보 · 비밀번호 변경
 * - PUT    /me                                            : 내 이름·연락처
 *
 * 백엔드: /users · PUT /me 는 #28(user/UserController), /me · /me/password 는 AuthController — 주소 · 보내는 값은 이 파일과 같음.
 * 예시 데이터와의 작은 차이는 backend-notes 14장.
 * 예시 모드에서는 로그인한 사람(auth.user)의 권한 범위로 거름 — 실제는 서버가 토큰으로 거름
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import * as mock from './mock/userData'
import { useAuthStore } from '@/stores/auth'

const clean = (obj) => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== '' && v != null))
const reply = (r) => (r.error ? mockFail(r.error) : mockOk(r.data))
/** 예시 모드: 지금 로그인한 사람 */
const actor = () => useAuthStore().user

/** @returns {{ counts, inactiveCount, items, page, size, total }} */
export function getUsers(params = {}) {
  if (USE_MOCK) return mockOk(mock.list(actor(), params))
  return http.get('/users', { params: clean(params) })
}

export function getUser(id) {
  if (USE_MOCK) return reply(mock.detail(actor(), id))
  return http.get(`/users/${id}`)
}

/** @returns {{ available: boolean }} */
export function checkLoginId(loginId) {
  if (USE_MOCK) return mockOk(mock.checkLoginId(loginId))
  return http.get('/users/check-login-id', { params: { loginId } })
}

/** @param {{ name, loginId, phone, role, scope: { siteId, factoryIds, processIds } }} body */
export function createUser(body) {
  if (USE_MOCK) return reply(mock.create(actor(), body))
  return http.post('/users', body)
}

/** @param {{ name, phone, role, scope }} body */
export function updateUser(id, body) {
  if (USE_MOCK) return reply(mock.update(actor(), id, body))
  return http.put(`/users/${id}`, body)
}

export function setUserActive(id, active) {
  if (USE_MOCK) return reply(mock.setStatus(actor(), id, active))
  return http.patch(`/users/${id}/status`, { active })
}

export function deleteUser(id) {
  if (USE_MOCK) return reply(mock.remove(actor(), id))
  return http.delete(`/users/${id}`)
}

/** @returns {{ tempPassword }} */
export function resetUserPassword(id) {
  if (USE_MOCK) return reply(mock.resetPassword(actor(), id))
  return http.post(`/users/${id}/reset-password`)
}

/** 내 정보 (백엔드 MeView: id, loginId, name, phone, role, scope, pwResetRequired, lastLoginAt) */
export function getMe() {
  if (USE_MOCK) return reply(mock.me(actor()))
  return http.get('/me')
}

export function updateMe(body) {
  if (USE_MOCK) return reply(mock.updateMe(actor(), body))
  return http.put('/me', body)
}

/** 비밀번호 변경 { currentPassword, newPassword } */
/**
 * 비밀번호 변경 — 지금 비밀번호가 틀리면 백엔드가 401 PASSWORD_MISMATCH 를 줌
 * → 로그아웃하지 않고 창에 문구 (http.js skipAuthLogout). 진짜 토큰 만료면 원래대로 로그아웃
 */
const PASSWORD_CHECK = { skipAuthLogout: ['PASSWORD_MISMATCH'] }
export function changeMyPassword(body) {
  if (USE_MOCK) {
    const r = mock.changePassword(actor(), body)
    return r.error ? mockFail(r.error, PASSWORD_CHECK) : mockOk(r.data)
  }
  return http.put('/me/password', body, PASSWORD_CHECK)
}
