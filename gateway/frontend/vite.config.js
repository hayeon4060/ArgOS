import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// Vite 설정
// - '@' 별칭: import 경로를 '@/api/http' 처럼 src 기준으로 쓰기 위함
// - 개발 서버 프록시: /api 요청을 연계 서버(gateway/backend, 기본 8080)로 전달
//   (mock 모드에서는 요청을 보내지 않으므로 백엔드가 꺼져 있어도 됨)
// - /phone 은 공정 담당자 앱(argos/phone, 개발 서버 5174)으로 전달 (같은 주소로 열기)
// - mockSync: 예시 데이터 모드 시연용 중계 — 개발 서버가 예시 상황 하나를 들고, 바뀌면 열린 화면 모두에 바로 보냄
//   PC 관제 웹과 휴대폰 담당자 앱처럼 다른 기기끼리도 같은 상황을 봄 (src/api/mock/scenario.js)
//   개발 서버에서만 동작하고 배포 빌드에는 들어가지 않음. 실제 서버(VITE_USE_MOCK=false)에서는 쓰이지 않음
function mockSync() {
  let state = null // 마지막 예시 상황 (JSON 글자)
  const clients = new Set() // 열려 있는 화면들 (SSE)
  return {
    name: 'argos-mock-sync',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/__mock/scenario', (req, res) => {
        // GET /__mock/scenario/stream : 바뀔 때마다 받기
        if (req.method === 'GET' && req.url.startsWith('/stream')) {
          res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' })
          res.write(': connected\n\n')
          clients.add(res)
          req.on('close', () => clients.delete(res))
          return
        }
        // POST /__mock/scenario : 바뀐 상황 올리기 → 모두에게
        if (req.method === 'POST') {
          let body = ''
          req.on('data', (chunk) => (body += chunk))
          req.on('end', () => {
            try {
              body = JSON.stringify(JSON.parse(body)) // 한 줄 JSON 만 (SSE data 한 줄)
            } catch {
              res.statusCode = 400
              res.end()
              return
            }
            state = body
            for (const c of clients) c.write(`data: ${body}\n\n`)
            res.statusCode = 204
            res.end()
          })
          return
        }
        // GET /__mock/scenario : 지금 상황 (아직 없으면 null)
        res.setHeader('Content-Type', 'application/json')
        res.end(state ?? 'null')
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), mockSync()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/phone': 'http://localhost:5174',
    },
  },
})
