/**
 * 시연용 휴대폰 알림 (웹 푸시) — 예시 데이터 모드 + 개발 서버(npm run dev)에서 브라우저로 열었을 때만
 * - 크롬 웹 푸시는 구글 FCM 으로 전달됨 → 휴대폰 화면이 꺼져 있거나 크롬을 닫아도 알림이 옴
 * - 보내는 쪽: 관제 웹 개발 서버(mockSync.js)가 체크리스트 전송 · 다시 알림 · 담당자 변경을 보면 보냄
 * - 알림 띄우기 · 누르면 그 체크리스트 열기: public/sw.js (서비스 워커)
 * - 크롬은 https 또는 localhost 주소에서만 알림을 켤 수 있음 → 휴대폰(http://PC주소)은 크롬 설정이 필요 (README)
 * - 설치형 앱(APK)·실제 서버 모드에서는 쓰지 않음 (그때는 push.js 의 FCM)
 */
import { ref } from 'vue'
import { USE_MOCK } from '@/api/mock'

const API = '/__mock/push'
const BASE = import.meta.env.BASE_URL // '/phone/'

/**
 * 지금 상태 (위 바 아래 안내 줄이 씀)
 * null: 쓸 수 없음(앱·실제 서버·푸시 없는 브라우저) | 'insecure': http 주소라 못 켬 | 'off': 아직 안 켬 | 'denied': 크롬에서 막음 | 'on': 받는 중
 */
export const webPush = ref(null)

const toB64url = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
const fromB64url = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0))
const post = (path, body) =>
  fetch(`${API}/${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

/** 로그인 뒤 한 번: 이미 허용했으면 조용히 다시 등록 (개발 서버를 다시 켰거나 다른 사람이 로그인했을 수 있음) */
export async function initWebPush(userId) {
  if (!import.meta.env.DEV || !USE_MOCK) return
  if (!window.isSecureContext) return (webPush.value = 'insecure')
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return
  if (Notification.permission === 'denied') return (webPush.value = 'denied')
  if (Notification.permission === 'granted') return subscribe(userId)
  webPush.value = 'off'
}

/** '알림 켜기' 단추 (허용 묻기는 사람이 누를 때만 — 크롬이 그냥 묻는 것은 막음) */
export async function enableWebPush(userId) {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return (webPush.value = permission === 'denied' ? 'denied' : 'off')
  return subscribe(userId)
}

async function subscribe(userId) {
  try {
    await navigator.serviceWorker.register(`${BASE}sw.js`, { scope: BASE })
    const reg = await navigator.serviceWorker.ready
    const { publicKey } = await fetch(`${API}/key`).then((r) => r.json())
    let sub = await reg.pushManager.getSubscription()
    // 개발 서버 키가 바뀌었으면(.mock-push.json 을 지움 등) 다시 구독
    if (sub && toB64url(sub.options.applicationServerKey) !== publicKey) {
      await sub.unsubscribe()
      sub = null
    }
    sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: fromB64url(publicKey) })
    const res = await post('subscribe', { userId, endpoint: sub.endpoint })
    if (!res.ok) throw new Error(`구독 등록 실패 ${res.status}`)
    webPush.value = 'on'
  } catch (e) {
    console.warn('[web push]', e)
    webPush.value = 'off'
  }
}

/** 로그아웃: 이 브라우저로는 이 사람 알림을 그만 보냄 (허용은 그대로 — 다음 사람이 로그인하면 그 사람으로 다시 등록) */
export async function stopWebPush() {
  if (webPush.value !== 'on') return
  const sub = await (await navigator.serviceWorker.getRegistration(BASE))?.pushManager.getSubscription()
  if (sub) await post('unsubscribe', { endpoint: sub.endpoint }).catch(() => {})
  webPush.value = null
}
