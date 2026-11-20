/**
 * 화면 전환 방향 정하기 (설계안 js/shell.js route() 의 zoom-in / zoom-out 과 같은 규칙)
 * - 라우터(router/index.js)가 화면을 옮기기 직전에 decideTransition 을 부르고,
 *   레이아웃(MainLayout)의 PageTransition 이 여기 값으로 효과를 고름
 * - 대시보드 단계 깊이(meta.depth): 깊어지면 'zoom-in', 얕아지면 'zoom-out'
 * - 그 밖(메뉴 탭 이동, 같은 깊이): 'page' (살짝 나타남)
 * - 확대 중심: 마지막으로 누른 위치 (예: 지도에서 누른 5공장 구역) → 그쪽으로 빨려 들어가듯
 */
import { reactive } from 'vue'

export const transitionState = reactive({
  /** Transition 이름: 'zoom-in' | 'zoom-out' | 'page' */
  name: 'page',
  /** 확대·축소 중심 (화면 기준 px) */
  originX: 0,
  originY: 0,
})

// 마지막으로 누른 위치 (화면 기준) — 한 번만 등록
let lastPointer = null
if (typeof window !== 'undefined') {
  window.addEventListener(
    'pointerdown',
    (e) => {
      lastPointer = { x: e.clientX, y: e.clientY }
    },
    { capture: true },
  )
}

/** 이동할 때마다 라우터가 부름 */
export function decideTransition(to, from) {
  const a = from.meta?.depth
  const b = to.meta?.depth
  if (a != null && b != null && a !== b) {
    transitionState.name = b > a ? 'zoom-in' : 'zoom-out'
  } else {
    transitionState.name = 'page'
  }
  /*
   * 확대·축소 중심
   * - 줌 인: 누른 곳 (그쪽으로 빨려 들어가듯). 누른 곳이 없으면 화면 가운데
   * - 줌 아웃: 항상 화면 가운데
   *   (예전에는 누른 곳 = 왼쪽 위 경로 글자를 중심으로 축소해서, 화면 오른쪽 끝만 크게 움직여
   *    좌우로 쏠리며 흔들려 보였음 → 가운데를 중심으로 바꿔 양쪽이 똑같이 움직이게 함)
   */
  const useClick = transitionState.name === 'zoom-in' && lastPointer
  transitionState.originX = useClick ? lastPointer.x : window.innerWidth / 2
  transitionState.originY = useClick ? lastPointer.y : window.innerHeight / 2
  lastPointer = null
}
