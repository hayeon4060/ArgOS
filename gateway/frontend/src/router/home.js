/**
 * 권한별 첫 화면
 * - 로그인 직후, 권한 없는 주소로 들어왔을 때, 로고를 눌렀을 때 이동할 곳
 * - 별도 파일로 둔 이유: 상단 바 같은 컴포넌트가 router/index.js 를 import 하면 순환이 생김
 */
import { ROLE } from '@/types/codes'

/**
 * 공장 담당자의 첫 화면 (가정 — 재원님 확정 전)
 * - 'site-dashboard': 사업장 현황 (화면설계안 HOME.factory = 'dash/site')  ← 지금 값
 * - 'dashboard'     : 담당 공장 대시보드 (API 명세서 firstPage = FACTORY_DASHBOARD)
 * 확정되면 이 한 줄만 바꾸면 됨
 */
export const PLANT_ADMIN_HOME = 'site-dashboard'

/** 로그인 사용자의 담당 범위 (로그인 응답 user.scope) */
export function scopeOf(user) {
  return {
    siteId: user?.scope?.siteId ?? null,
    factoryIds: user?.scope?.factoryIds ?? [],
  }
}

export function homeRouteOf(user) {
  if (user?.role === ROLE.HQ_ADMIN) {
    return { name: 'site-select' }
  }
  const { siteId, factoryIds } = scopeOf(user)
  if (user?.role === ROLE.PLANT_ADMIN && siteId) {
    if (PLANT_ADMIN_HOME === 'dashboard' && factoryIds[0]) {
      return { name: 'dashboard', params: { siteId, factoryId: factoryIds[0] } }
    }
    return { name: 'site-dashboard', params: { siteId } }
  }
  return { name: 'login' }
}
