/*
 * 시연용 휴대폰 알림 서비스 워커 (src/webPush.js 가 등록 — 예시 데이터 모드 + 개발 서버에서만)
 * - 크롬이 FCM 으로 푸시를 받으면 화면이 꺼져 있거나 크롬이 닫혀 있어도 이 파일이 깨어남
 * - 푸시에는 내용이 없음 → 개발 서버(관제 웹 mockSync.js POST /__mock/push/last)에서 문구 · 열 화면을 받아 알림을 띄움
 *   (개발 서버에 못 닿으면 기본 문구)
 * - 알림을 누르면 그 화면(체크리스트)을 엶: 열려 있는 앱 창이 있으면 그 창을, 없으면 새 창
 */
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  event.waitUntil(
    (async () => {
      let msg = null
      try {
        const sub = await self.registration.pushManager.getSubscription()
        const res = await fetch('/__mock/push/last', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ endpoint: sub?.endpoint }),
        })
        msg = await res.json()
      } catch {
        // 개발 서버에 못 닿음 → 기본 문구
      }
      msg ??= { title: 'ArgOS 담당자', body: '새 알림이 있습니다. 눌러서 확인하세요.', url: '#/checklists', tag: 'argos' }
      await self.registration.showNotification(msg.title, {
        body: msg.body,
        tag: msg.tag, // 같은 체크리스트 알림은 한 장으로 (다시 알림이면 다시 울림)
        renotify: true,
        requireInteraction: true, // 누르거나 지울 때까지 남김
        data: { url: new URL(msg.url, self.registration.scope).href },
      })
    })(),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.url ?? self.registration.scope
  event.waitUntil(
    (async () => {
      const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      const win = wins.find((w) => w.url.startsWith(self.registration.scope))
      if (win) {
        try {
          await win.focus()
          return await win.navigate(url)
        } catch {
          // 이 워커가 맡지 않은 창이면 새 창으로
        }
      }
      return self.clients.openWindow(url)
    })(),
  )
})
