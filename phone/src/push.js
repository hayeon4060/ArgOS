/**
 * 휴대폰 푸시 알림 (FCM, @capacitor/push-notifications) — 설치형 앱(APK)에서만
 * - 로그인 뒤 PhoneLayout 이 startPush(): 알림 허용 묻기 → FCM 토큰 받기 → 토큰 등록
 *   · 실제 서버: POST /me/push-token → 서버(PushSender)가 보냄
 *   · 시연 빌드(예시 데이터 + VITE_MOCK_RELAY): PC 개발 서버(관제 웹 mockSync.js)에 등록 → 개발 서버가 FCM 으로 보냄
 *   → 앱이 꺼져 있거나 화면이 꺼져 있어도 휴대폰 알림으로 옴
 * - 알림을 누르면 onOpen(checklistId), 앱이 켜져 있을 때 오면 onReceive() (화면 안 알림 띠는 PhoneLayout 이 띄움)
 * - 로그아웃 때 stopPush(): 토큰 지우기 (다른 사람이 이 폰으로 로그인해도 앞사람 알림이 안 오게)
 * - android/app/google-services.json(Firebase)이 없이 빌드했으면 아무것도 안 함 (vite.config.js __FCM__)
 * - 브라우저에서는 아무것도 안 함 (브라우저 시연 알림은 webPush.js)
 */
import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'
import http from '@/api/http'
import { USE_MOCK } from '@/api/mock'

/** 서버 PushSender · 개발 서버 mockSync.js 의 channel_id 와 같게. 중요도 5(최대) = 화면 위로 뜨는 알림 + 소리 */
export const CHANNEL_ID = 'argos_alert'

/** 시연 빌드에서 토큰을 등록할 PC 개발 서버: 로그인 화면에서 바꾼 주소(argos.relay) → .env.production.local */
export const RELAY = savedRelay() || (import.meta.env.VITE_MOCK_RELAY ?? '')
function savedRelay() {
  try {
    return localStorage.getItem('argos.relay') ?? ''
  } catch {
    return ''
  }
}

/** 로그인 화면 'PC 주소' 저장 — 예시 상황 연결(scenario.js)도 이 값을 읽으므로 저장 뒤 앱을 다시 띄움 */
export function saveRelay(url) {
  try {
    if (url) localStorage.setItem('argos.relay', url)
    else localStorage.removeItem('argos.relay')
  } catch {
    // 저장 못 하면 빌드 때 주소 그대로
  }
  location.reload()
}
// __FCM__: 빌드할 때 google-services.json 이 있었는지 (vite.config.js define)
const enabled = Capacitor.isNativePlatform() && __FCM__ && (!USE_MOCK || !!RELAY)

let token = null

/** 시연 빌드: PC 개발 서버에 토큰 등록 · 해제 */
function relay(path, body) {
  return fetch(`${RELAY}/__mock/push/${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
}

export async function startPush({ userId, onOpen, onReceive }) {
  if (!enabled) return
  await PushNotifications.removeAllListeners()
  PushNotifications.addListener('registration', ({ value }) => {
    token = value
    const saved = USE_MOCK
      ? relay('subscribe', { userId, fcmToken: value })
      : http.post('/me/push-token', { fcmToken: value, platform: Capacitor.getPlatform(), appVersion: null })
    saved.catch((e) => console.warn('[push] 토큰 등록 실패', e))
  })
  PushNotifications.addListener('registrationError', (e) => console.warn('[push] 등록 실패', e))
  PushNotifications.addListener('pushNotificationReceived', () => onReceive())
  PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
    const id = notification.data?.checklistId
    if (id) onOpen(Number(id))
  })

  await PushNotifications.createChannel({ id: CHANNEL_ID, name: '체크리스트 · 호출 알림', importance: 5, visibility: 1, vibration: true })
  let { receive } = await PushNotifications.checkPermissions()
  if (receive === 'prompt' || receive === 'prompt-with-rationale') ({ receive } = await PushNotifications.requestPermissions())
  if (receive !== 'granted') return // 거절하면 앱이 켜져 있을 때 알림 띠만 뜸
  await PushNotifications.register()
}

export async function stopPush() {
  if (!enabled) return
  if (USE_MOCK) await relay('unsubscribe', { fcmToken: token }).catch(() => {})
  else await http.delete('/me/push-token').catch(() => {})
  await PushNotifications.removeAllListeners()
  await PushNotifications.unregister().catch(() => {})
}
