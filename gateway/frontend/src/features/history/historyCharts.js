/**
 * 통계 화면 ECharts 설정 (화면설계안 vStats() 의 c1 · c2)
 * - trendOption  : 일별 알림 추이 — 주의(주황)·경보(빨강) 누적 막대 + 7일 평균 선 (오늘·주별이면 선 없음)
 * - processOption: 공정별 알림 — 공정마다 센서 종류별 누적 가로 막대 + 오른쪽 합계
 * - sensorSeriesOption: 알림 상세 패널 — 센서 3종 측정값 (처음 넘은 시각 ~ 조치 완료)
 */
import { CHART_COLORS, SENSOR_CHART_COLOR } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'
import { SENSOR_LABEL } from './historyFilter'

const AXIS = {
  axisLine: { lineStyle: { color: CHART_COLORS.grid } },
  axisTick: { show: false },
  axisLabel: { color: CHART_COLORS.axis, fontSize: 12, fontFamily: 'Fira Code, monospace' },
  splitLine: { lineStyle: { color: CHART_COLORS.grid } },
}

/** 추이 칸 이름: 'YYYY-MM-DD' → 'M/D', 'YYYY-MM-DDTHH:00' → 'HH시', 'YYYY-Www' → 'n주' */
export function keyLabel(key, unit) {
  if (unit === 'HOUR') return `${key.slice(11, 13)}시`
  if (unit === 'WEEK') return `${Number(key.slice(6))}주`
  return `${Number(key.slice(5, 7))}/${Number(key.slice(8, 10))}`
}

/**
 * @param {{ unit, points }} trend GET /stats/trend
 * @param {boolean} showShift 근무조가 '통합'이면 툴팁에 주간·야간 건수 (화면설계서)
 */
export function trendOption(trend, showShift) {
  const { unit, points } = trend
  const total = points.map((p) => p.warn + p.alarm)
  // 7일 이동 평균 (일별일 때만)
  const avg = total.map((_, i) => {
    const a = total.slice(Math.max(0, i - 6), i + 1)
    return Math.round((a.reduce((s, v) => s + v, 0) / a.length) * 100) / 100
  })
  const series = [
    { name: '주의', type: 'bar', stack: 'a', data: points.map((p) => p.warn), barMaxWidth: 54, itemStyle: { color: CHART_COLORS.warn } },
    { name: '경보', type: 'bar', stack: 'a', data: points.map((p) => p.alarm), itemStyle: { color: CHART_COLORS.alarm, borderRadius: [2, 2, 0, 0] } },
  ]
  if (unit === 'DAY') {
    series.push({ name: '7일 평균', type: 'line', data: avg, symbol: 'none', smooth: 0.3, lineStyle: { color: '#334155', width: 2 } })
  }
  return {
    ...chartMotion(),
    grid: { left: 48, right: 28, top: 34, bottom: 34 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (items) => {
        const p = points[items[0].dataIndex]
        const head = `<b>${keyLabel(p.key, unit)}</b> · ${p.warn + p.alarm}건`
        const rows = items.map((it) => `${it.marker}${it.seriesName} ${it.value}${it.seriesName === '7일 평균' ? '' : '건'}`)
        if (showShift) rows.push(`주간 ${p.day}건 · 야간 ${p.night}건`)
        return [head, ...rows].join('<br/>')
      },
    },
    legend: { show: false },
    xAxis: {
      type: 'category',
      data: points.map((p) => keyLabel(p.key, unit)),
      ...AXIS,
      axisLabel: { ...AXIS.axisLabel, interval: points.length > 20 ? 2 : 0 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      max: (v) => Math.max(5, v.max),
      name: '건',
      nameTextStyle: { color: CHART_COLORS.axis, fontSize: 11, padding: [0, 30, 0, 0] },
      ...AXIS,
      axisLine: { show: false },
    },
    series,
  }
}

/**
 * @param {Array} rows GET /stats/by-process (많은 순)
 */
export function processOption(rows) {
  // 열화상(IR) 알림이 있을 때만 그 막대를 넣음
  const types = ['CURRENT', 'TEMP', 'CO', ...(rows.some((r) => r.IR) ? ['IR'] : [])]
  const last = types.length - 1
  return {
    ...chartMotion(),
    grid: { left: 96, right: 64, top: 22, bottom: 34 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', minInterval: 1, ...AXIS, axisLine: { show: false } },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((r) => r.processName),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: CHART_COLORS.ink, fontSize: 13.5, fontWeight: 500 },
    },
    series: types.map((t, i) => ({
      name: SENSOR_LABEL[t],
      type: 'bar',
      stack: 'a',
      barWidth: 26,
      data: rows.map((r) => r[t]),
      itemStyle: { color: SENSOR_CHART_COLOR[t], borderColor: '#fff', borderWidth: 1 },
      // 마지막 조각 오른쪽에 공정 합계
      label:
        i === last
          ? { show: true, position: 'right', formatter: (p) => `${rows[p.dataIndex].total}건`, color: CHART_COLORS.ink, fontFamily: 'Fira Code, monospace', fontSize: 13, fontWeight: 600 }
          : { show: false },
    })),
  }
}

/**
 * 알림 상세 패널: 센서 3종(전류·온도·CO) 측정값 — 세로로 3칸, 같은 시간축
 * - 처음 기준을 넘은 시각 ~ 조치 완료 (GET /stats/alarms/{id} 의 series)
 * - 칸마다 주의(주황)·경보(빨강) 기준 점선, 알림을 낸 센서는 선을 굵게 + 옅은 바탕
 * - 마우스를 올리면 세 칸 모두 같은 시각 값을 보여 줌 (axisPointer link)
 * @param {{ sensors: Array }} series
 * @param {number} rowHeight 칸 하나 높이(px)
 */
export function sensorSeriesOption(series, rowHeight = 96) {
  const sensors = series.sensors
  const top = 10
  const gap = 14
  const time = (v) => {
    const d = new Date(v)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  const timeSec = (v) => `${time(v)}:${String(new Date(v).getSeconds()).padStart(2, '0')}`
  return {
    ...chartMotion(),
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    tooltip: {
      trigger: 'axis',
      formatter: (items) => {
        const head = timeSec(items[0].value[0])
        const rows = items.map((it) => `${it.marker}${it.seriesName} <b>${it.value[1]}</b> ${sensors[it.seriesIndex].unit}`)
        return [head, ...rows].join('<br>')
      },
    },
    grid: sensors.map((_, i) => ({ left: 46, right: 64, top: top + i * (rowHeight + gap), height: rowHeight })),
    xAxis: sensors.map((_, i) => ({
      type: 'time',
      gridIndex: i,
      min: series.from,
      max: series.to,
      ...AXIS,
      splitLine: { show: false },
      // 시각 글자는 맨 아래 칸에만
      axisLabel: { ...AXIS.axisLabel, fontSize: 11, show: i === sensors.length - 1, formatter: time, hideOverlap: true },
    })),
    yAxis: sensors.map((s, i) => {
      const values = s.points.map((p) => p[1])
      const lo = Math.min(...values)
      const hi = Math.max(s.alarm, ...values)
      const pad = (hi - lo) * 0.12 || 1
      return {
        type: 'value',
        gridIndex: i,
        min: Math.floor(lo - pad),
        max: Math.ceil(hi + pad),
        splitNumber: 2,
        ...AXIS,
        axisLabel: { ...AXIS.axisLabel, fontSize: 11 },
      }
    }),
    series: sensors.map((s, i) => ({
      name: `${SENSOR_LABEL[s.type]} ${s.tag}`,
      type: 'line',
      xAxisIndex: i,
      yAxisIndex: i,
      data: s.points,
      showSymbol: false,
      smooth: 0.2,
      lineStyle: { color: SENSOR_CHART_COLOR[s.type], width: s.trigger ? 2.2 : 1.6 },
      itemStyle: { color: SENSOR_CHART_COLOR[s.type] },
      areaStyle: s.trigger ? { color: SENSOR_CHART_COLOR[s.type], opacity: 0.06 } : undefined,
      markLine: {
        silent: true,
        symbol: 'none',
        animation: false,
        data: [
          { yAxis: s.warn, lineStyle: { color: CHART_COLORS.warn, type: 'dashed', width: 1 }, label: { formatter: `주의 ${s.warn}`, color: '#c2410c', fontSize: 10.5 } },
          { yAxis: s.alarm, lineStyle: { color: CHART_COLORS.alarm, type: 'dashed', width: 1 }, label: { formatter: `경보 ${s.alarm}`, color: '#b91c1c', fontSize: 10.5 } },
        ],
      },
    })),
  }
}
