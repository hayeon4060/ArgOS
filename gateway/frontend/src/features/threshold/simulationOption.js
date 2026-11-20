/**
 * 변경 시뮬레이션 그래프 ECharts 설정 (화면설계안 vThreshold() preview() 의 차트)
 * - 최근 7일 30분 간격 선 (센서 종류 색), 아래 옅은 채움
 * - 점선: 경보 기준(빨강), 주의 기준(주황) — 바꾸는 중이면 '현재'(회색) · '변경'(파랑) 선
 * - 넘었던 순간 점: 지금 기준으로도 넘음(주의 주황 / 경보 빨강), 바꾼 기준으로만 넘음(파랑), 안 넘음(회색)
 * - 가로축: 날짜만 (하루 한 번)
 */
import { CHART_COLORS, SENSOR_CHART_COLOR } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'
import { fmt } from './thresholdForm'

/** 센서 종류 색 (설계안 SC) */
export const TYPE_COLOR = SENSOR_CHART_COLOR

const toMs = (iso) => new Date(iso).getTime()
const md = (ms) => {
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const line = (y, color, label, width = 1.5, bold = false) => ({
  yAxis: y,
  lineStyle: { color, type: 'dashed', width },
  label: { formatter: label, color, fontSize: 12, fontWeight: bold ? 700 : 400, fontFamily: 'Pretendard' },
})

/**
 * 그래프에 찍을 '넘었던 순간' 점 [{ at, value }]
 * - 예시 데이터 · 명세서: sim.peaks 를 그대로 씀
 * - 지금 백엔드: sim.hits (넘은 시각만, 값 없음 — central ThresholdService) → 그 시각에 가장 가까운 추이 값을 붙임
 * @param {object|null} sim POST /thresholds/simulate 결과
 * @param {object|null} series GET /sensors/series 한 줄 { points: [[at, value]] }
 */
export function peaksOf(sim, series) {
  if (Array.isArray(sim?.peaks)) return sim.peaks
  const points = series?.points ?? []
  if (!Array.isArray(sim?.hits) || !points.length) return []
  return sim.hits.map((at) => {
    const t = toMs(at)
    const near = points.reduce((best, p) => (Math.abs(toMs(p[0]) - t) < Math.abs(toMs(best[0]) - t) ? p : best))
    return { at, value: near[1] }
  })
}

/**
 * @param {object} p
 * @param {object} p.series   GET /sensors/series 한 줄 { points }
 * @param {Array}  p.peaks    넘었던 순간 [{ at, value }]
 * @param {string} p.type     센서 종류
 * @param {string} p.unit
 * @param {'warn'|'alarm'} p.field 지금 고치는 칸
 * @param {{ warn, alarm }} p.saved 저장된 기준
 * @param {{ warn, alarm }} p.next  입력 중인 기준
 */
export function simulationOption({ series, peaks, type, unit, field, saved, next }) {
  const color = TYPE_COLOR[type] ?? CHART_COLORS.action
  const before = saved[field]
  const after = next[field]
  const changed = after !== before

  const lines = [line(next.alarm, CHART_COLORS.alarm, `경보 ${fmt(next.alarm)}`)]
  if (changed) {
    lines.push(line(before, CHART_COLORS.axis, `현재 ${fmt(before)}`))
    lines.push(line(after, CHART_COLORS.action, `변경 ${fmt(after)}`, 2, true))
  }
  if (!changed || field === 'alarm') lines.push(line(next.warn, CHART_COLORS.warn, `주의 ${fmt(next.warn)}`))

  // 점 색: 지금 기준으로 넘음 → 단계 색, 바꾼 기준으로만 넘음 → 파랑, 안 넘음 → 회색
  const hit = field === 'warn' ? CHART_COLORS.warn : CHART_COLORS.alarm
  const dots = peaks.map((p) => ({
    coord: [toMs(p.at), p.value],
    itemStyle: { color: p.value >= before ? hit : p.value >= after ? CHART_COLORS.action : '#cbd5e1', borderColor: '#fff', borderWidth: 2 },
  }))

  const values = series.points.map((p) => p[1])
  const lo = Math.floor(Math.min(...values) - (next.warn - Math.min(...values)) * 0.4)
  const hi = Math.ceil(Math.max(next.alarm, ...values, ...peaks.map((p) => p.value)) * 1.06)

  return {
    ...chartMotion(),
    tooltip: { trigger: 'axis', valueFormatter: (v) => `${v} ${unit}` },
    grid: { left: 56, right: 104, top: 16, bottom: 34 },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: CHART_COLORS.grid } },
      axisTick: { show: false },
      splitLine: { show: false },
      splitNumber: 7,
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11, formatter: (v) => md(v), hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      min: Math.max(0, lo),
      max: hi,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: CHART_COLORS.grid } },
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11, formatter: `{value} ${unit}` },
    },
    series: [
      {
        name: '값',
        type: 'line',
        data: series.points.map(([t, v]) => [toMs(t), v]),
        symbol: 'none',
        lineStyle: { width: 2, color },
        itemStyle: { color },
        areaStyle: { color: `${color}12` },
        // 기준선이 옮겨 갈 때 0.3초 (설계안 markLine animationDuration 300)
        markLine: { symbol: 'none', silent: true, animationDuration: 300, animationDurationUpdate: 300, data: lines },
        markPoint: { symbol: 'circle', symbolSize: 11, label: { show: false }, data: dots },
      },
    ],
  }
}
