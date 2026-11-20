/**
 * ECharts 설정
 * - 번들 크기를 줄이기 위해 실제로 쓰는 차트·컴포넌트만 등록
 * - 새 차트 종류가 필요하면 아래 use([...]) 에 추가
 * - 화면에서는 전역 등록된 <VChart :option="..." /> 로 사용 (main.js 참고)
 */
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart, GaugeChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent, // 주의·경보 기준선
  MarkPointComponent, // 추이 그래프 끝의 지금 값 점
  MarkAreaComponent, // 경보 기준 위 옅은 빨간 바탕
  DataZoomComponent, // 추이 그래프 휠 확대·축소, 끌어서 이동
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  GaugeChart, // 설비 상세 센서 게이지
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent,
  MarkPointComponent,
  MarkAreaComponent,
  DataZoomComponent,
])

/**
 * 차트 움직임 (화면설계서 R2: 처음 그리기 500ms, 값 바뀜 400ms)
 * - 운영체제 '움직임 줄이기'가 켜져 있으면 움직이지 않음
 * - 사용: option 에 ...chartMotion() 으로 넣기
 */
export function chartMotion() {
  const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reduced) return { animation: false }
  return { animationDuration: 500, animationDurationUpdate: 400, animationEasing: 'cubicOut' }
}

export default VChart
