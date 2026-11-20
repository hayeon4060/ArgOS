/**
 * 센서 추이 그래프 ECharts 설정 (설계안 js/lib.js trend(), vEquip() drawTrends())
 * - 오늘 선: 상태 색 + 아래 옅은 채움, 끝에 지금 값 점과 숫자
 * - 주의·경보 기준선(점선), 경보 기준 위는 옅은 빨간 바탕
 * - 전날 선: 회색 점선 (어제 같은 시각 값을 오늘 시각으로 옮겨 겹쳐 그림)
 * - 마우스 휠 확대·축소, 끌어서 이동 (dataZoom inside). 세 그래프는 TrendCard 에서 한 묶음으로 같이 움직임
 */
import { CHART_COLORS, LEVEL_CHART_COLOR } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * 선 색: 정상 파랑, 주의 주황 #F97316, 경보·화재 빨강 #DC2626
 * (설계안은 주의 선이 진한 주황 #EA580C — 2026-11-20 게이지·배지와 같은 단계 색으로 맞춤)
 */
const LINE = { ...LEVEL_CHART_COLOR, NORMAL: CHART_COLORS.action }

/** 세로축 끝: 경보 기준보다 15% 넉넉하게, 보기 좋은 숫자로 올림 (예: 27 A → 35, 180 ℃ → 210) */
export function niceMax(alarm, dataMax = 0) {
  const v = Math.max(alarm * 1.15, dataMax * 1.05)
  const step = v < 50 ? 5 : v < 300 ? 10 : 50
  return Math.ceil(v / step) * step
}

const toMs = (iso) => new Date(iso).getTime()

/**
 * @param {object} p
 * @param {object} p.series  GET /sensors/series 의 series 한 개 ({ points, compare, warn, alarm, unit })
 * @param {string} p.level   센서 단계 (선 색)
 * @param {number} p.dec     소수 자릿수
 * @param {number|null} p.liveValue 지금 값 (마지막 점을 이 값으로)
 * @param {boolean} p.showPrev 전날 선 보이기
 * @param {boolean} p.wholeDay '오늘' 범위면 가로축을 0시~24시로
 * @param {{ start: number, end: number }} p.zoom 확대 구간 (%)
 */
export function trendOption({ series, level, dec, liveValue, showPrev, wholeDay, zoom }) {
  const color = LINE[level] ?? LINE.NORMAL
  const data = series.points.map(([t, v]) => [toMs(t), v])
  if (data.length && liveValue != null) data[data.length - 1] = [data[data.length - 1][0], liveValue]
  const last = data[data.length - 1]
  const prev = series.compare.map(([t, v]) => [toMs(t) + DAY_MS, v]) // 어제 → 오늘 시각으로
  const max = niceMax(series.alarm, Math.max(...data.map((d) => d[1]), 0))

  // 가로축 범위: '오늘'은 하루 전체, 그 밖은 받은 처음~끝
  const start = data[0]?.[0]
  const dayStart = start != null ? new Date(start).setHours(0, 0, 0, 0) : undefined
  const xMin = wholeDay ? dayStart : start
  const xMax = wholeDay && dayStart != null ? dayStart + DAY_MS : last?.[0]

  const fmt = (v) => (v == null ? '-' : Number(v).toFixed(dec))

  return {
    ...chartMotion(), // 처음 그릴 때 선이 왼쪽에서 오른쪽으로 500ms, 값 바뀜 400ms
    grid: { left: 46, right: 80, top: 16, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      // 같은 시각의 오늘 / 전날 값
      formatter: (items) => {
        const time = new Date(items[0].value[0]).toTimeString().slice(0, 5)
        const rows = items.map((it) => `${it.marker}${it.seriesName} <b>${fmt(it.value[1])}</b> ${series.unit}`)
        return [time, ...rows].join('<br/>')
      },
    },
    dataZoom: [{ type: 'inside', start: zoom.start, end: zoom.end, zoomOnMouseWheel: true, moveOnMouseMove: true }],
    xAxis: {
      type: 'time',
      min: xMin,
      max: xMax,
      axisLine: { lineStyle: { color: CHART_COLORS.grid } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11, formatter: '{HH}:{mm}', hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max,
      splitNumber: 4,
      name: series.unit,
      nameTextStyle: { color: CHART_COLORS.axis, fontSize: 11, padding: [0, 30, 0, 0] },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: CHART_COLORS.grid } },
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11 },
    },
    series: [
      {
        name: '오늘',
        type: 'line',
        data,
        smooth: 0.3,
        symbol: 'none',
        z: 2,
        lineStyle: { width: 2.5, color },
        itemStyle: { color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${color}30` },
              { offset: 1, color: `${color}00` },
            ],
          },
        },
        markLine: {
          symbol: 'none',
          silent: true,
          label: { show: false },
          data: [
            { yAxis: series.warn, lineStyle: { color: CHART_COLORS.warn, type: 'dashed', width: 1.5 } },
            { yAxis: series.alarm, lineStyle: { color: CHART_COLORS.alarm, type: 'dashed', width: 1.5 } },
          ],
        },
        markArea: { silent: true, itemStyle: { color: 'rgba(220, 38, 38, 0.05)' }, data: [[{ yAxis: series.alarm }, { yAxis: max }]] },
        // 끝 점 + 지금 값 글자
        markPoint: last
          ? {
              symbol: 'circle',
              symbolSize: 10,
              itemStyle: { color, borderColor: '#fff', borderWidth: 2 },
              label: {
                show: true,
                position: 'right',
                distance: 6,
                formatter: `${fmt(last[1])} ${series.unit}`,
                color: CHART_COLORS.ink,
                fontFamily: 'Fira Code',
                fontWeight: 600,
                fontSize: 12,
              },
              data: [{ coord: last }],
            }
          : undefined,
      },
      {
        name: '전날',
        type: 'line',
        data: showPrev ? prev : [],
        smooth: 0.3,
        symbol: 'none',
        z: 1,
        lineStyle: { width: 1.5, color: CHART_COLORS.prev, type: 'dashed' },
        itemStyle: { color: CHART_COLORS.prev },
        // 켜고 끌 때 200ms 로 빠르게 (화면설계서 B4 '전날 선은 200ms')
        animationDuration: 200,
        animationDurationUpdate: 200,
      },
    ],
  }
}
