/**
 * 라우터 (화면 주소 정의 + 권한 가드)
 *
 * 화면 흐름 (화면설계안 js/shell.js ROUTES 기준):
 *  로그인 → 권한 분기 (router/home.js)
 *   - 본사 관리자(HQ_ADMIN): 사업장 선택(/sites) → 사업장 현황(/sites/1)
 *   - 공장 담당자(PLANT_ADMIN): 담당 사업장 현황(/sites/1) 으로 바로 (가정, home.js 참고)
 *   - 공정 담당자(WORKER): 모바일 앱 사용 (웹 진입 불가)
 *  사업장 현황 → 공장 대시보드(/sites/1/factories/5) → (다음 작업) 공정 → 설비
 *  상단 메뉴: 대시보드 / 통계·이력 / 점검 + 관리(사용자·공장·이용 로그)
 *  (설비 기준값 관리는 2026-11-19 부터 공장 관리 안의 창)
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ROLE, WEB_ROLES } from '@/types/codes'

import BlankLayout from '@/layouts/BlankLayout.vue'
import MainLayout from '@/layouts/MainLayout.vue'
import { decideTransition } from '@/features/motion/pageTransition'
import { homeRouteOf, scopeOf } from './home'
import { MENU_ROUTES } from './menus'

// 다른 파일에서 '@/router' 로도 쓸 수 있게 다시 내보냄
export { homeRouteOf }

const routes = [
  // 로그인: 상단 바 없는 빈 레이아웃
  {
    path: '/',
    component: BlankLayout,
    children: [
      {
        path: 'login',
        name: 'login',
        component: () => import('@/pages/auth/LoginPage.vue'),
        meta: { public: true }, // 로그인 없이 접근 가능
      },
    ],
  },

  /*
   * 상단 바 + 본문 (사업장 선택부터 설비 상세까지 한 레이아웃)
   * - 한 레이아웃이라 화면이 바뀌어도 상단 바는 그대로 있고 본문만 전환 효과를 냄
   * - meta.plain: 메뉴 없는 상단 바 (설계안 'hdr plain', 사업장 선택)
   * - meta.depth: 대시보드 단계 깊이 (사업장 선택 0 → 사업장 1 → 공장 2 → 공정 3 → 설비 4)
   *   깊어지면 확대(줌 인), 얕아지면 축소(줌 아웃) — features/motion/pageTransition.js
   */
  {
    path: '/sites',
    component: MainLayout,
    children: [
      {
        path: '',
        name: 'site-select',
        component: () => import('@/pages/site/SiteSelectPage.vue'),
        meta: { roles: [ROLE.HQ_ADMIN], title: '사업장 선택', plain: true, depth: 0 }, // 본사 관리자만
      },
      {
        path: ':siteId',
        name: 'site-dashboard',
        component: () => import('@/pages/dashboard/SiteDashboardPage.vue'),
        meta: { roles: WEB_ROLES, nav: 'dashboard', title: '사업장 현황', depth: 1 },
      },
      {
        path: ':siteId/factories/:factoryId',
        name: 'dashboard',
        component: () => import('@/pages/dashboard/FactoryDashboardPage.vue'),
        meta: { roles: WEB_ROLES, nav: 'dashboard', title: '공장 대시보드', depth: 2 },
      },
      {
        path: ':siteId/factories/:factoryId/processes/:processId',
        name: 'process',
        component: () => import('@/pages/dashboard/ProcessPage.vue'),
        meta: { roles: WEB_ROLES, nav: 'dashboard', title: '3D 설비 배치도', depth: 3 },
      },
      {
        path: ':siteId/factories/:factoryId/processes/:processId/equipment/:equipmentId',
        name: 'equipment',
        component: () => import('@/pages/dashboard/EquipmentPage.vue'),
        meta: { roles: WEB_ROLES, nav: 'dashboard', title: '설비 상세', depth: 4 },
      },
      // 메뉴 화면 (깊이 없음 → 탭 이동처럼 부드럽게 바뀜)
      ...MENU_ROUTES.map((menu) => ({
        path: `:siteId/${menu.path}`,
        name: menu.name,
        component: menu.component,
        meta: { roles: WEB_ROLES, nav: menu.nav, title: menu.title },
      })),
    ],
  },

  // 그 외 주소: 첫 화면으로
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 권한 가드: 화면 이동 전마다 실행
 * - 반환값 true: 이동 허용 / 라우트 객체: 그 화면으로 대신 이동
 */
router.beforeEach((to) => {
  const auth = useAuthStore()

  // 0) 웹에서 갈 곳이 없는 사용자(WORKER, 담당 범위 없는 공장 담당자)는 저장된 로그인 정보 정리
  //    (안 하면 로그인 화면 ↔ 첫 화면 사이에서 무한 이동이 생김)
  if (auth.isLoggedIn && homeRouteOf(auth.user).name === 'login') {
    auth.logout({ callServer: false })
  }

  // 0-2) 비밀번호를 먼저 바꿔야 하는 계정이 바꾸지 않은 채(변경 창에서 새로고침 등) 다른 화면으로 가려 하면
  //      로그인 정보를 지워 다시 로그인하게 함 (그대로 두면 서버가 모든 API 를 403 으로 막아 빈 화면만 보임)
  if (auth.isLoggedIn && auth.pwResetRequired) {
    auth.logout({ callServer: false })
  }

  // 0-1) 로그인 유지 시간(13시간)이 지났으면 로그아웃 후 로그인 화면에 안내
  //      (화면을 켜 둔 채 지난 경우는 상단 바 SessionTimer 가 처리, 여기서는 새로고침·주소 이동 때)
  //      callServer: false → 로그인 정보를 바로(기다리지 않고) 지움.
  //      서버 요청을 기다리면 그 사이 로그인 화면 가드가 '아직 로그인 상태'로 보고 다시 되돌려 보냄
  if (auth.isExpired()) {
    auth.logout({ callServer: false })
    return { name: 'login', query: { reason: 'expired' } }
  }

  // 1) 로그인 화면
  if (to.meta.public) {
    // 이미 로그인했으면 첫 화면으로
    return auth.isLoggedIn ? homeRouteOf(auth.user) : true
  }

  // 2) 로그인 안 했으면 로그인 화면으로 (로그인 후 돌아올 주소를 redirect 로 전달)
  if (!auth.isLoggedIn) {
    // '/' 는 돌아올 의미가 없으므로 redirect 생략
    const query = to.fullPath === '/' ? {} : { redirect: to.fullPath }
    return { name: 'login', query }
  }

  // 3) 주소가 '/' 뿐이면 권한별 첫 화면으로
  if (to.path === '/') {
    return homeRouteOf(auth.user)
  }

  // 4) 화면별 허용 권한 검사 (부모 라우트의 meta 도 합쳐져서 들어옴)
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    return homeRouteOf(auth.user)
  }

  // 5) 공장 담당자는 담당 사업장(scope.siteId)과 담당 공장(scope.factoryIds)만 볼 수 있음
  //    (주소의 ID 는 문자열이라 숫자로 바꿔 비교)
  if (auth.role === ROLE.PLANT_ADMIN) {
    const { siteId, factoryIds } = scopeOf(auth.user)
    const wrongSite = to.params.siteId && Number(to.params.siteId) !== siteId
    const wrongFactory = to.params.factoryId && !factoryIds.includes(Number(to.params.factoryId))
    if (wrongSite || wrongFactory) {
      return homeRouteOf(auth.user)
    }
  }

  return true
})

// 화면 전환 방향(줌 인 / 줌 아웃 / 탭 이동)은 여기 한 곳에서 정함
router.beforeResolve((to, from) => {
  decideTransition(to, from)
})

export default router
