/**
 * 앱 시작점
 * - 글꼴·전역 스타일 → Pinia → 라우터 → ECharts 순서로 등록
 */
import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'

// 글꼴 (npm 패키지로 포함 → 외부 인터넷이 막힌 공장망에서도 표시됨)
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import '@fontsource/fira-code/500.css'
import '@fontsource/fira-code/600.css'

// 디자인 토큰 → 전역 기본 스타일 순서 (base.css 가 토큰을 사용)
import '@/assets/styles/tokens.css'
import '@/assets/styles/base.css'
// 화면 전환·카드 등장·말풍선 등 움직임 (tokens.css 의 --m-* 값 사용)
import '@/assets/styles/motion.css'
// 알림 대응 창 공통 모양 (주의 상세·체크리스트 진행 등)
import '@/assets/styles/alert.css'
// 주의·경보·화재 점멸 (지도·카드·목록 줄·알림 띠 공통)
import '@/assets/styles/blink.css'

import App from './App.vue'
import router from './router'
import VChart from '@/utils/echarts'
import { INIT_OPTIONS_KEY } from 'vue-echarts'
import { chartPixelRatio, initScreenScale } from '@/utils/screenScale'
import { startBlinkSync } from '@/utils/blink'
import { setAuthHandlers } from '@/api/http'
import { syncServerClock } from '@/api/clock'
import { startLive, stopLive } from '@/api/live'
import { USE_MOCK } from '@/api/mock'
import { useAuthStore } from '@/stores/auth'

// 화면 배율 자동 맞춤 (FHD 1배, QHD 1.333배) — 화면을 그리기 전에 먼저 적용
initScreenScale()
// 주의·경보 점멸 박자 맞추기 (늦게 나타난 카드·말풍선도 같은 순간에 깜빡이게)
startBlinkSync()

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 차트 컴포넌트 전역 등록: 어느 화면에서든 <VChart :option="option" /> 로 사용
app.component('VChart', VChart)
// 배율이 걸려도 그래프가 흐려지지 않게, 그릴 때의 픽셀 비율을 배율만큼 올림
app.provide(INIT_OPTIONS_KEY, () => ({ devicePixelRatio: chartPixelRatio() }))

// Axios 에 토큰 조회 방법과 401(인증 만료) 처리 방법 등록
const auth = useAuthStore(pinia)
setAuthHandlers({
  getToken: () => auth.accessToken,
  onUnauthorized: async () => {
    await auth.logout({ callServer: false })
    router.push({ name: 'login' })
  },
})

// 실제 서버 모드: 로그인 중이면 시연 시계를 맞추고 실시간(SSE) 연결, 로그아웃하면 끊음
if (!USE_MOCK) {
  watch(
    () => auth.accessToken,
    (token) => {
      if (token) {
        syncServerClock()
        startLive(() => auth.accessToken)
      } else {
        stopLive()
      }
    },
    { immediate: true },
  )
}

app.mount('#app')
