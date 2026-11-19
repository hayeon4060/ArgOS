import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { mockSync } from './mockSync.js'

// Vite 설정
// - '@' 별칭: import 경로를 '@/api/http' 처럼 src 기준으로 쓰기 위함
// - 개발 서버 프록시: /api 요청을 연계 서버(gateway/backend, 기본 8080)로 전달
//   (mock 모드에서는 요청을 보내지 않으므로 백엔드가 꺼져 있어도 됨)
// - mockSync: 예시 상황 중계 + 시연용 휴대폰 알림 (개발 서버에서만, mockSync.js)
export default defineConfig({
  plugins: [vue(), mockSync()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true, // 같은 Wi-Fi 의 다른 PC · 휴대폰도 http://이PC주소:5173 으로 열어 같은 예시 상황을 봄
    proxy: {
      '/api': 'http://localhost:8080',
      // 담당자 앱(../../../phone, npm run dev 5174)을 /phone/ 으로 같이 띄움 (휴대폰에서 주소 하나로)
      '/phone': { target: 'http://localhost:5174', ws: true },
    },
  },
})
