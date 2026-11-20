/**
 * 공정 담당자 앱 시작점
 * - 관제 웹과 같은 글꼴·디자인 토큰·스타일, 같은 로그인 스토어·API 를 씀 ('@' = 웹 src, vite.config.js)
 * - 나중에 Capacitor 로 Android 앱을 만들 때 이 폴더를 그대로 감쌈 (webDir: dist)
 */
import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'

import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import '@fontsource/fira-code/500.css'
import '@fontsource/fira-code/600.css'

import '@/assets/styles/tokens.css'
import '@/assets/styles/base.css'
import '@/assets/styles/motion.css'

import App from '@/App.vue'
import router from './router'
import { setAuthHandlers } from '@/api/http'
import { syncServerClock } from '@/api/clock'
import { startLive, stopLive } from '@/api/live'
import { USE_MOCK } from '@/api/mock'
import { useAuthStore } from '@/stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const auth = useAuthStore(pinia)
setAuthHandlers({
  getToken: () => auth.accessToken,
  onUnauthorized: async () => {
    await auth.logout({ callServer: false })
    router.push({ name: 'login' })
  },
})

// 실제 서버 모드: 로그인 중이면 시연 시계를 맞추고 실시간(SSE) 연결, 로그아웃하면 끊음 (웹 main.js 와 같게)
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
