/**
 * 화면 파일을 받는 동안의 뼈대 (라우터 이동이 0.2초 넘게 걸릴 때)
 *
 * - 화면은 모두 지연 로딩(() => import(...))이라 처음 여는 화면은 파일을 받는 동안 이전 화면이 그대로 멈춰 있음
 *   → 이동이 0.2초(--m-skeleton-delay) 넘게 걸리면 본문(PageTransition) 자리에 가려는 화면의 뼈대를 보여 줌
 * - 뼈대 모양: 가려는 라우트의 meta.skeleton ('dashboard' | 'table'), 칸 배치는 라우트 이름으로 찾음
 * - 전환 효과
 *   · 이전 화면 → 뼈대: 원래 정해진 효과(줌 인 · 줌 아웃 · page) 그대로 (방향은 뼈대가 나올 때 정함)
 *   · 뼈대 → 새 화면: 효과 없이 바로 바꿈 (같은 뼈대가 이어지다 카드가 BaseCard 의 rise 로 올라옴)
 * - 이어받기(carry): 뼈대가 보이던 중에 화면이 열리면, 그 화면의 데이터 뼈대(PageSkeleton)는 0.2초 기다리지 않고 바로
 *   → 뼈대 → 빈 화면 → 뼈대 로 깜빡이지 않음
 * - 하지 않는 경우: 로그인 화면에서 들어올 때 · 새로고침 직후 첫 이동 (로그인 직후 전체 화면 로딩은 그대로 둠)
 *
 * 사용: router/index.js 에서 권한 가드 다음에 installRouteSkeleton(router) 한 번
 */
import { reactive } from 'vue'
import { START_LOCATION } from 'vue-router'
import { decideTransition, transitionState } from './pageTransition'

/** PageTransition 이 보는 상태 */
export const routeSkeleton = reactive({
  /** 뼈대를 보여 주는 중 */
  visible: false,
  /** 뼈대 모양 'dashboard' | 'table' */
  shape: 'dashboard',
  /** 가려는 화면(라우트) 이름 → 칸 배치 */
  layout: '',
})

/** 뼈대 → 새 화면 전환 이름 (PageTransition 이 이 이름이면 효과 없이 바로 바꿈) */
export const SKELETON_SWAP = 'skeleton-swap'

let timer = null
/** 지금 기다리는 이동 (빨리 여러 번 누르면 마지막 이동만 뼈대를 켜고 끔) */
let pendingTo = null
/** 방금 뼈대가 보이고 있었는지 (다음 PageSkeleton 이 한 번 이어받음) */
let carry = false

/** tokens.css 의 --m-skeleton-delay (ms) */
function delayMs() {
  const value = getComputedStyle(document.documentElement).getPropertyValue('--m-skeleton-delay')
  return parseFloat(value) || 200
}

/** 이동 시작: 뼈대를 쓸 화면이면 0.2초 뒤에 켜지도록 예약 */
function start(to, from) {
  clearTimeout(timer)
  carry = false
  pendingTo = null
  // 로그인 화면에서 들어올 때 · 새로고침 직후(이전 화면 없음) · 뼈대가 없는 화면은 하지 않음
  if (!to.meta.skeleton || from === START_LOCATION || from.name === 'login') return
  pendingTo = to
  timer = setTimeout(() => {
    if (pendingTo !== to) return
    // 전환 방향(줌 인·아웃)은 지금 정함 → beforeResolve 에서는 다시 정하지 않음
    decideTransition(to, from)
    Object.assign(routeSkeleton, { visible: true, shape: to.meta.skeleton, layout: String(to.name ?? '') })
  }, delayMs())
}

/** 이동 끝(성공 · 취소 · 오류): 예약을 지우고, 보이던 뼈대는 효과 없이 새 화면으로 바꿈 */
function finish(to) {
  if (to && pendingTo && to !== pendingTo) return // 이미 다음 이동이 시작됨 (그쪽이 끔)
  clearTimeout(timer)
  pendingTo = null
  if (!routeSkeleton.visible) return
  carry = true
  transitionState.name = SKELETON_SWAP
  routeSkeleton.visible = false
}

/**
 * 뼈대가 보이던 중이었는지 한 번 확인 (PageSkeleton 이 처음 그릴 때 부름)
 * - true 면 0.2초 기다리지 않고 바로 보임. 한 번 쓰면 지움
 */
export function consumeSkeletonCarry() {
  const was = carry
  carry = false
  return was
}

/**
 * 라우터에 연결 (router/index.js)
 * - 권한 가드 다음에 등록 → 가드를 통과한(실제로 이동할) 경우만 예약됨
 * - 전환 방향은 원래처럼 beforeResolve 에서 정하되, 뼈대가 이미 보이면 건너뜀 (뼈대가 나올 때 정했음)
 */
export function installRouteSkeleton(router) {
  router.beforeEach((to, from) => {
    start(to, from)
  })
  router.beforeResolve((to, from) => {
    if (!routeSkeleton.visible) decideTransition(to, from)
  })
  router.afterEach((to) => finish(to))
  // 화면 파일을 못 받으면 afterEach 가 오지 않음 → 여기서 끔 (이전 화면이 다시 보임)
  router.onError((_error, to) => finish(to))
}
