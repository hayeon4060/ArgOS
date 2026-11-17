/**
 * 담당자 앱 라우터
 *
 * 화면 (수행계획서 '앱 화면', API 명세서 M1·M2):
 *  로그인(#/login) → 공정 현황(#/) : 담당 공정 설비 상태
 *                → 체크리스트(#/checklists) : 공장 담당자에게 받은 목록 → 한 건(#/checklists/:id) 체크 · 완료 보고
 *
 * - 해시 주소: Capacitor 로 감쌌을 때도 서버 설정 없이 동작
 * - 공정 담당자(WORKER)만. 관리자는 로그인 단계에서 막힘 (웹 api/auth.js, client: 'APP' → CLIENT_NOT_ALLOWED)
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import PhoneLayout from './layouts/PhoneLayout.vue'

const routes = [
  { path: '/login', name: 'login', component: () => import('./pages/LoginPage.vue'), meta: { public: true } },
  {
    path: '/',
    component: PhoneLayout,
    // meta.tab: 아래 탭 표시, meta.back: 위 바 뒤로 단추가 갈 곳
    children: [
      { path: '', name: 'home', component: () => import('./pages/HomePage.vue'), meta: { tab: 'home', title: '공정 현황' } },
      {
        path: 'checklists',
        name: 'checklists',
        component: () => import('./pages/ChecklistListPage.vue'),
        meta: { tab: 'checklists', title: '체크리스트' },
      },
      {
        path: 'checklists/:checklistId',
        name: 'checklist',
        component: () => import('./pages/ChecklistPage.vue'),
        meta: { tab: 'checklists', title: '설비 점검', back: 'checklists' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  // 로그인 유지 시간(13시간)이 지났으면 로그아웃 후 안내
  if (auth.isExpired()) {
    auth.logout({ callServer: false })
    return { name: 'login', query: { reason: 'expired' } }
  }

  // 공정 담당자가 아닌 로그인 정보가 남아 있으면 정리
  if (auth.isLoggedIn && auth.role !== ROLE.WORKER) auth.logout({ callServer: false })

  if (to.meta.public) return auth.isLoggedIn ? { name: 'home' } : true
  if (!auth.isLoggedIn) return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  return true
})

export default router
