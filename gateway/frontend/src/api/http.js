/**
 * Axios 공통 인스턴스
 *
 * 백엔드와 아직 합의 전인 가정 (바뀌면 이 파일만 수정):
 *  - 기본 주소: /api/v1
 *  - 성공 응답: { success: true,  data: ... }
 *  - 실패 응답: { success: false, error: { code, message } }
 *  - 인증: Authorization: Bearer <액세스 토큰>
 *
 * 화면/API 모듈에서는 응답의 data 만 받아서 쓰도록 여기서 꺼내 줌.
 * 실패하면 항상 ApiError 를 던지므로 화면에서는 error.message 만 보여 주면 됨.
 *
 * 401 처리 (로그인 상태에서 401 이면 로그아웃 → 로그인 화면)
 *  - 예외: 요청 설정에 skipAuthLogout: ['오류 코드', …] 를 주면, 401 이어도 그 코드면 로그아웃하지 않고
 *    오류를 그대로 부른 쪽에 넘김 (비밀번호를 다시 확인하는 요청 — 백엔드가 '비밀번호 틀림'도 401 로 줘서)
 *    · PUT /me/password            → PASSWORD_MISMATCH       (api/users.js)
 *    · POST /fires/{id}/clear      → FIRE_PASSWORD_MISMATCH  (api/emergency.js)
 *  - 같은 요청이라도 진짜 토큰 만료(TOKEN_EXPIRED · AUTH_REQUIRED 등 목록에 없는 코드)면 원래대로 로그아웃
 */
import axios from 'axios'

/** API 실패를 한 가지 모양으로 통일한 에러 */
export class ApiError extends Error {
  constructor({ code, message, status, details = null }) {
    super(message)
    this.name = 'ApiError'
    this.code = code // 백엔드 오류 코드 (예: AUTH_INVALID)
    this.status = status // HTTP 상태 코드 (네트워크 오류면 0)
    this.details = details // 오류에 붙은 나머지 값 (예: 화재 해제 error.remaining 남은 횟수)
  }
}

// 토큰 조회와 401 처리 방법은 main.js 에서 주입 (http.js 가 스토어·라우터를 직접 import 하지 않도록)
const handlers = {
  getToken: () => null,
  onUnauthorized: () => {},
}

/** main.js 에서 한 번 호출: 토큰을 어디서 읽을지, 401 이면 무엇을 할지 등록 */
export function setAuthHandlers({ getToken, onUnauthorized }) {
  handlers.getToken = getToken
  handlers.onUnauthorized = onUnauthorized
}

/**
 * 401 이면 로그아웃할지 정함 (실제 요청 · 예시 데이터 mockFail 이 같이 씀)
 * @param {number} status HTTP 상태
 * @param {string} code 오류 코드
 * @param {string[]} [skip] 요청별 skipAuthLogout — 이 코드면 로그아웃하지 않음
 */
export function handleUnauthorized(status, code, skip) {
  if (status !== 401 || !handlers.getToken()) return // 로그인 전 401 = 아이디 · 비밀번호 오류 (화면이 문구만)
  if (skip?.includes(code)) return // 비밀번호 다시 확인 요청의 '틀림' — 부른 쪽이 문구로 보여 줌
  handlers.onUnauthorized()
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000, // 10초 안에 응답 없으면 실패 처리
})

// 요청 인터셉터: 로그인 상태면 토큰 헤더 붙이기
http.interceptors.request.use((config) => {
  const token = handlers.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 응답 인터셉터: 성공이면 data 만 반환, 실패면 ApiError 로 변환
http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 형식이 { success, data } 가 아니면(파일 다운로드 등) 그대로 반환
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) return body.data
      throw new ApiError({
        code: body.error?.code ?? 'UNKNOWN',
        message: body.error?.message ?? '요청을 처리하지 못했습니다.',
        status: response.status,
      })
    }
    return body
  },
  (error) => {
    // 서버가 응답을 준 경우
    if (error.response) {
      const { status, data } = error.response

      const code = data?.error?.code ?? `HTTP_${status}`

      // 401: 토큰 만료 등 → 로그아웃 후 로그인 화면으로 (요청별 skipAuthLogout 예외는 위 handleUnauthorized)
      // (리프레시 토큰은 쓰지 않음: 토큰 13시간 유효 A안 확정 — 만료되면 다시 로그인)
      handleUnauthorized(status, code, error.config?.skipAuthLogout)

      const { code: _c, message: _m, ...details } = data?.error ?? {}
      throw new ApiError({
        code,
        message: data?.error?.message ?? '요청을 처리하지 못했습니다.',
        status,
        details,
      })
    }

    // 서버 응답 자체가 없는 경우 (서버 꺼짐, 네트워크 끊김, 시간 초과)
    throw new ApiError({
      code: 'NETWORK_ERROR',
      message: '서버에 연결할 수 없습니다.',
      status: 0,
    })
  },
)

export default http
