import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

// Vite 설정
// - '@' 별칭: import 경로를 '@/api/http' 처럼 src 기준으로 쓰기 위함
// - 개발 서버 프록시: /api 요청을 연계 서버(gateway/backend, 기본 8080)로 전달
//   브라우저는 같은 주소(5173)로 요청하고 Vite 가 대신 전달하므로 CORS 에 걸리지 않음
//   (mock 모드에서는 요청을 보내지 않으므로 백엔드가 꺼져 있어도 됨)
export default defineConfig(({ mode }) => {
  // vite.config.js 안에서는 import.meta.env 를 못 쓰므로 loadEnv 로 .env / .env.local 값을 직접 읽음
  const env = loadEnv(mode, process.cwd())

  // 프록시 대상 주소. 다른 PC의 서버에 붙을 때는 커밋하지 않는 .env.local 에
  // VITE_PROXY_TARGET=http://<서버 IP>:8080 한 줄만 넣으면 됨 (비우면 내 PC의 8080)
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': proxyTarget,
      },
    },
  }
})
