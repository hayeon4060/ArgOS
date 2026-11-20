/**
 * 가스 농도 측정 그래프 (화면설계안 vInspect() rec('gas'))
 * - 측정 시각별 CO 막대 + '기준 30 ppm 미만' 빨간 점선
 * - 확인 필요 값(아직 확인 전)은 주황 막대, 기준을 넘으면 빨강
 */
import { CHART_COLORS } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'

/**
 * @param {Array} rows [{ label: '08:50 작업 전', value: 4, open: false }]
 * @param {number} limit 기준(ppm)
 */
export function gasOption(rows, limit = 30) {
  return {
    ...chartMotion(),
    grid: { left: 34, right: 112, top: 14, bottom: 26 }, // right: 기준선 글자("기준 30 ppm 미만")가 잘리지 않을 만큼
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.label),
      axisLine: { lineStyle: { color: CHART_COLORS.grid } },
      axisTick: { show: false },
      axisLabel: { color: CHART_COLORS.axis, fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      max: Math.max(40, ...rows.map((r) => r.value || 0)),
      interval: 10,
      axisLabel: { color: CHART_COLORS.axis, fontSize: 12, fontFamily: 'Fira Code, monospace' },
      splitLine: { lineStyle: { color: CHART_COLORS.grid } },
    },
    series: [
      {
        type: 'bar',
        barWidth: 34,
        data: rows.map((r) => ({
          value: r.value,
          itemStyle: { color: r.value >= limit ? CHART_COLORS.alarm : r.open ? CHART_COLORS.warn : '#3b82f6', borderRadius: [2, 2, 0, 0] },
        })),
        label: { show: true, position: 'top', color: CHART_COLORS.ink, fontFamily: 'Fira Code, monospace', fontSize: 12.5, fontWeight: 600, formatter: '{c} ppm' },
        markLine: {
          symbol: 'none',
          silent: true,
          data: [
            {
              yAxis: limit,
              lineStyle: { color: CHART_COLORS.alarm, type: 'dashed', width: 1.5 },
              label: { formatter: `기준 ${limit} ppm 미만`, color: '#b91c1c', fontSize: 12, position: 'end' },
            },
          ],
        },
      },
    ],
  }
}
