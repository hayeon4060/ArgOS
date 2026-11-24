/**
 * 센서 게이지 ECharts 설정 (설계안 js/lib.js gauge() 와 같은 모양)
 * - 바깥 얇은 고리: 기준 눈금 (0~주의 초록, 주의~경보 주황, 경보~끝 빨강)
 * - 안쪽 굵은 고리: 지금 값만큼 칠함 (색 = 센서 단계), 가운데 큰 숫자 + 단위
 * - 가운데 숫자 색도 센서 단계 (정상 초록 · 주의 주황 · 경보 빨강, 2026-11-22 재원님 요청). 값이 없으면 기본 글자색
 * - 220° → -40° (아래가 트인 고리)
 */
import { CHART_COLORS, LEVEL_CHART_COLOR } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'

const ARC = { startAngle: 220, endAngle: -40 }
const HIDE = { pointer: { show: false }, axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false } }

/**
 * @param {{ value: number, max: number, warn: number, alarm: number, unit: string, level: string, dec: number,
 *           fontSize?: number, width?: number }} s  fontSize·width: 숫자 크기·고리 굵기 (경보 창의 작은 게이지는 22 · 9)
 */
export function gaugeOption({ value, max, warn, alarm, unit, level, dec, fontSize = 34, width = 14 }) {
  // 값이 아직 없으면(서버에 값이 안 들어옴) 고리는 비우고 숫자는 '–'
  const has = Number.isFinite(value)
  const levelColor = LEVEL_CHART_COLOR[level] ?? CHART_COLORS.normal
  // 기준이 없는 쪽은 눈금 색 칸을 건너뜀 (아래쪽 기준만 있는 센서면 warn·alarm 이 null → 예전엔 NaN 칸)
  const stop = (v) => (Number.isFinite(v) && max > 0 ? Math.min(1, v / max) : 1)
  return {
    ...chartMotion(), // 처음 500ms, 값 바뀜 400ms (바늘 대신 고리 길이·숫자가 움직임)
    series: [
      {
        type: 'gauge',
        ...ARC,
        ...HIDE,
        min: 0,
        max,
        radius: '96%',
        progress: { show: false },
        detail: { show: false },
        axisLine: {
          lineStyle: {
            width: 4,
            color: [
              [stop(warn), CHART_COLORS.normal],
              [stop(alarm), CHART_COLORS.warn],
              [1, CHART_COLORS.alarm],
            ],
          },
        },
      },
      {
        type: 'gauge',
        ...ARC,
        ...HIDE,
        min: 0,
        max,
        radius: '84%',
        axisLine: { roundCap: true, lineStyle: { width, color: [[1, CHART_COLORS.track]] } },
        progress: { show: true, width, roundCap: true, itemStyle: { color: levelColor } },
        detail: {
          valueAnimation: true, // 숫자도 같이 굴러감
          offsetCenter: [0, '-2%'],
          // 눈금 끝을 넘으면 고리는 끝까지만 칠하고 숫자는 실제 값 (화재 CO 120 ppm 등)
          formatter: (x) => `{v|${has ? (value > max && x >= max ? value : x).toFixed(dec) : '–'}}\n{u|${unit}}`,
          rich: {
            v: { fontFamily: 'Fira Code', fontSize, fontWeight: 600, color: has ? levelColor : CHART_COLORS.ink, lineHeight: fontSize + 4 },
            u: { fontSize: 12, color: CHART_COLORS.axis, lineHeight: 16 },
          },
        },
        // 눈금 끝을 넘으면 끝까지만 칠함 (설계안과 같음. 눈금 끝은 경보 기준보다 넉넉해서 보통은 넘지 않음)
        data: [{ value: has ? Math.min(value, max) : 0 }],
      },
    ],
  }
}
