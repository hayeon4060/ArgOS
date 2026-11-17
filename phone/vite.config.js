import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 공정 담당자 앱 Vite 설정
// - 관제 웹 프론트엔드의 src 를 '@' 로 그대로 가져다 씀 (예시 데이터·API·스토어·공통 부품이 웹과 같음)
//   다른 위치에 붙이려면: ARGOS_WEB=../다른/gateway/frontend npm run dev
// - '@phone': 이 앱 코드 (phone/src)
// - '@/utils/storage' 만 앱 것(src/storage.js)으로 바꿔 끼움: 로그인을 웹(argos.auth)과 따로 저장
//   → 한 브라우저에서 공장 담당자 웹과 공정 담당자 앱을 같이 켜 둘 수 있음
// - dedupe: 웹 src 의 vue·pinia 등도 이 폴더 node_modules 하나로 (두 벌이면 스토어·반응성이 따로 놂)
// - 개발(npm run dev): 주소 /phone/, 포트 5174. 관제 웹 개발 서버(5173)가 /phone 을 여기로 넘겨 주므로
//   http://localhost:5173/phone/ 로 열면 웹 창과 예시 상황(localStorage)을 같이 봄
// - 빌드(npm run build → dist): 상대 주소('./') — Capacitor 가 dist 를 앱 안에 넣어 그대로 띄움 (capacitor.config.json)
const here = (p) => fileURLToPath(new URL(p, import.meta.url))
// 관제 웹 위치: ARGOS_WEB → 저장소 안(../gateway/frontend) → 압축 풀어 둔 사본(../ArgOS-develop/...) 순서
const web = process.env.ARGOS_WEB
  ? path.resolve(process.env.ARGOS_WEB)
  : [here('../gateway/frontend'), here('../ArgOS-develop/ArgOS-develop/gateway/frontend')].find((p) => existsSync(p))

export default defineConfig(({ command }) => ({
  base: command === 'serve' ? '/phone/' : './',
  plugins: [vue()],
  resolve: {
    alias: [
      { find: /^@\/utils\/storage(\.js)?$/, replacement: here('./src/storage.js') },
      { find: '@phone', replacement: here('./src') },
      { find: '@', replacement: path.join(web, 'src') },
    ],
    dedupe: ['vue', 'vue-router', 'pinia', 'axios'],
  },
  // android/ 안의 빌드 사본(assets/public/index.html)은 보지 않음 (cap sync 때마다 새로고침되지 않게)
  optimizeDeps: { entries: ['index.html'] },
  server: {
    port: 5174,
    strictPort: true,
    hmr: { clientPort: 5174 }, // 5173/phone/ 으로 열어도 화면 갱신은 이 서버에 바로 연결
    fs: { allow: [here('.'), web] },
    watch: { ignored: ['**/android/**'] },
    proxy: {
      '/api': 'http://localhost:8080',
      // 예시 상황 중계는 관제 웹 개발 서버가 함 (5174 로 바로 열었을 때도 같은 상황을 보게)
      '/__mock': 'http://localhost:5173',
    },
  },
}))
