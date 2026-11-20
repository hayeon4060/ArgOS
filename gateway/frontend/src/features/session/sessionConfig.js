/**
 * 로그인 유지 시간 설정 (한 곳에서만 관리)
 * - 로그인하면 SESSION_HOURS 동안 유지, 상단 바 '연장'을 누르면 EXTEND_HOURS 만큼 늘어남
 * - 만료 WARN_BEFORE_MINUTES 분 전에 한 번 안내
 * - 백엔드와 합의 전 가정 (docs/backend-notes.md 참고)
 *
 * [개발할 때 빨리 확인하는 방법]
 *   gateway/frontend/.env 에 VITE_DEV_SESSION_MINUTES=2 처럼 넣고 npm run dev 다시 실행
 *   → 개발 모드(npm run dev)에서만 로그인 유지 시간이 2분이 됨 (빌드 결과물에는 적용 안 됨)
 *   → 안내는 만료 1분 전에 뜸 (유지 시간이 안내 시간보다 짧으면 절반 시점에 뜸)
 */

/** 로그인 유지 시간 (시간) */
export const SESSION_HOURS = 13

/** '연장' 한 번에 늘어나는 시간 (시간) */
export const EXTEND_HOURS = 1

/** 만료 몇 분 전에 안내할지 */
export const WARN_BEFORE_MINUTES = 10

const HOUR = 60 * 60 * 1000
const MINUTE = 60 * 1000

// 개발 모드에서만 짧게 바꿔 보기 (.env 의 VITE_DEV_SESSION_MINUTES)
const devMinutes = import.meta.env.DEV ? Number(import.meta.env.VITE_DEV_SESSION_MINUTES) || 0 : 0

/** 로그인 유지 시간 (ms) */
export const SESSION_MS = devMinutes ? devMinutes * MINUTE : SESSION_HOURS * HOUR

/** 연장 시간 (ms) */
export const EXTEND_MS = EXTEND_HOURS * HOUR

/** 안내 시점: 만료까지 남은 시간이 이 값 이하가 되면 (ms) */
export const WARN_BEFORE_MS = devMinutes
  ? Math.min(MINUTE, SESSION_MS / 2)
  : WARN_BEFORE_MINUTES * MINUTE
