/**
 * 차트(ECharts)용 색
 * - ECharts 는 캔버스에 그려서 CSS 변수(var(--...))를 읽지 못함 → 실제 색 값을 여기 둠
 * - 값은 assets/styles/tokens.css 와 같게 유지할 것 (색을 바꾸면 두 곳 다 바꾸기)
 */
export const CHART_COLORS = Object.freeze({
  warn: '#f97316', // --color-level-warn
  alarm: '#dc2626', // --color-level-alarm
  action: '#2563eb', // --color-primary (조치 건수 선)
  axis: '#94a3b8', // --color-text-weak
  grid: '#eef2f6',
  text: '#475569', // --color-text-sub
  normal: '#16a34a', // --color-level-normal
  ink: '#0f172a', // --color-text (게이지 가운데 숫자)
  track: '#eef2f6', // 게이지 빈 고리
  prev: '#94a3b8', // 추이 '전날' 점선
})

/** 상태 → 차트 색 (정상 초록 · 주의 주황 · 경보/화재 빨강) */
export const LEVEL_CHART_COLOR = Object.freeze({
  NORMAL: CHART_COLORS.normal,
  WARN: CHART_COLORS.warn,
  ALARM: CHART_COLORS.alarm,
  FIRE: CHART_COLORS.alarm,
})

/** 센서 종류 → 차트 색 (설계안 SC: 전류 파랑 · 온도 보라 · CO 청록) */
export const SENSOR_CHART_COLOR = Object.freeze({
  CURRENT: '#3b82f6',
  TEMP: '#8b5cf6',
  CO: '#0d9488',
  IR: '#64748b', // 설계안에 없음 — 주의 주황과 헷갈리지 않게 회색
})
