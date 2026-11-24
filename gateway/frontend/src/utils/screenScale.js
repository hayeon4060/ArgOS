/**
 * 화면 배율 자동 맞춤 (FHD · QHD)
 *
 * 화면은 FHD(가로 1920px) 기준으로 설계돼 있음 → 창이 그보다 넓으면 '창 가로 ÷ 1920' 만큼 전체를 확대
 *  - FHD(1920×1080): 1배 (설계 그대로)
 *  - QHD(2560×1440): 1.333배 → FHD 와 같은 배치·비율이 화면 끝까지 그대로 커짐
 *  - 1920 보다 좁은 창: 1배 (각 화면의 좁은 화면용 CSS 가 그대로 동작)
 *
 * 방법: <html> 에 CSS zoom 을 걸고, 같은 값을 CSS 변수 --screen-scale 로도 넘김
 *  - zoom 을 쓰면 글자·간격·그림·창(모달)까지 한 번에 같은 비율로 커짐 (화면별 CSS 를 고칠 필요 없음)
 *  - 주의 1) zoom 안에서는 100vh · 100vw 도 같이 커져서 화면을 넘침
 *            → 화면 높이·폭이 필요하면 tokens.css 의 var(--screen-h) · var(--screen-w) 를 씀
 *  - 주의 2) 마우스 위치(clientX 등)는 확대된 화면 기준 px, CSS 에 쓰는 px 은 확대 전 기준
 *            → 마우스로 잰 거리를 CSS 에 쓸 때는 toLayoutPx() 로 나눠서 씀
 */

/** 설계 기준 가로 폭 (FHD) */
const BASE_WIDTH = 1920
/** 배율 범위: 1배보다 작게 줄이지 않고(글자가 작아짐), 2배(4K 폭)까지만 */
const MIN_SCALE = 1
const MAX_SCALE = 2

let scale = 1

/** 지금 배율 (1 = FHD 기준 그대로) */
export const getScreenScale = () => scale

/** 확대된 화면 기준 px(마우스 위치·getBoundingClientRect) → CSS 에 쓰는 px */
export const toLayoutPx = (px) => px / scale

/** 창 크기에 맞는 배율을 계산해 <html> 에 적용 */
function apply() {
  const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, window.innerWidth / BASE_WIDTH))
  // 소수 셋째 자리까지만 (창을 조금 움직일 때마다 다시 그리지 않게)
  scale = Math.round(next * 1000) / 1000
  const root = document.documentElement
  root.style.zoom = scale === 1 ? '' : String(scale)
  root.style.setProperty('--screen-scale', String(scale))
}

/**
 * ECharts 마우스 위치 보정
 * - ECharts(zrender)는 마우스 위치를 event.offsetX 로 읽는데, zoom 안에서는 이 값이 확대된 화면 기준이라
 *   그래프 안 좌표(확대 전 기준)와 배율만큼 어긋남 → 말풍선·강조가 마우스와 다른 곳에 뜸
 * - zrender 는 이벤트에 zrX · zrY 가 이미 있으면 그 값을 그대로 쓰므로,
 *   그래프(canvas) 위 마우스 이벤트에 맞는 값을 먼저 넣어 줌 (capture 단계라 zrender 보다 먼저 실행)
 * - 비율은 'canvas 의 CSS 폭 ÷ 화면에 보이는 폭' 으로 직접 재서, 배율이 1이면 아무것도 바꾸지 않음
 */
const CHART_EVENTS = ['mousemove', 'mousedown', 'mouseup', 'click', 'dblclick', 'wheel', 'contextmenu']
function fixChartPointer(event) {
  const canvas = event.target
  if (scale === 1 || canvas?.tagName !== 'CANVAS') return
  const rect = canvas.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  event.zrX = (event.clientX - rect.left) * (canvas.offsetWidth / rect.width)
  event.zrY = (event.clientY - rect.top) * (canvas.offsetHeight / rect.height)
}

/** 앱 시작 때 한 번 호출 (main.js) */
export function initScreenScale() {
  apply()
  window.addEventListener('resize', apply)
  CHART_EVENTS.forEach((name) => document.addEventListener(name, fixChartPointer, { capture: true }))
}

/**
 * 그래프를 또렷하게: canvas 가 zoom 으로 늘어나 흐려지지 않도록 그릴 때의 픽셀 비율도 배율만큼 올림
 * (vue-echarts 의 init-options 로 넘김, main.js 참고)
 */
export const chartPixelRatio = () => (window.devicePixelRatio || 1) * scale
