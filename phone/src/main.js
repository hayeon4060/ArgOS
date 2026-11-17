/**
 * 공정 담당자 앱 시작점
 * - 관제 웹과 같은 글꼴·디자인 토큰·스타일, 같은 로그인 스토어·API 를 씀 ('@' = 웹 src, vite.config.js)
 * - 나중에 Capacitor 로 Android 앱을 만들 때 이 폴더를 그대로 감쌈 (webDir: dist)
 */
import { createApp } from 'vue'
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

app.mount('#app')
