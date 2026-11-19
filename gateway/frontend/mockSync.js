/**
 * 개발 서버 전용 중계 (vite.config.js plugins) — 실제 연계 서버가 생기면 쓰지 않음
 *
 * 1) 예시 상황 중계: 웹·담당자 앱(브라우저·설치형 앱 시연 빌드)이 같은 예시 상황을 보게 함
 *    - GET  /__mock/scenario        : { state } (아직 아무도 안 올렸으면 null)
 *    - POST /__mock/scenario        : { from, state } 저장 → 열린 화면 모두에 보냄
 *    - GET  /__mock/scenario/stream : SSE, 바뀔 때마다 data: { from, state }
 *    받는 쪽은 src/api/mock/scenario.js (scenarioSynced). 개발 서버를 다시 켜면 처음 상황부터
 *    설치형 앱은 다른 출처(http://localhost)라 /__mock 은 CORS 를 열어 둠
 *
 * 2) 시연용 휴대폰 알림: 체크리스트 전송 · 다시 알림 · 담당자 변경이 생기면 담당자에게 알림
 *    - 브라우저(크롬): 표준 웹 푸시(VAPID). 크롬은 FCM 으로 전달. 키는 처음 켤 때 만들어 .mock-push.json 에 둠 (커밋 금지)
 *      내용은 싣지 않고, 서비스 워커(담당자 앱 public/sw.js)가 POST /__mock/push/last 로 문구 · 열 화면을 받아 감
 *    - 설치형 앱(APK): FCM HTTP v1 로 직접 보냄. Firebase 서비스 계정 키 JSON 은 ARGOS_FCM_KEY 경로,
 *      없으면 사용자 폴더의 argos-fcm-key.json (저장소 밖에 둠). 둘 다 없으면 앱 알림은 생략
 *      알림 문구는 구글 서버를 거치므로 사람 이름 · 설비 이름 · 센서 값은 넣지 않음 (백엔드 PushSender 와 같은 원칙)
 *    - 둘 다 휴대폰 화면이 꺼져 있어도 옴 (높은 우선순위)
 *    - GET  /__mock/push/key         : { publicKey } (웹 푸시 구독할 때 씀)
 *    - POST /__mock/push/subscribe   : { userId, endpoint } (웹 푸시) 또는 { userId, fcmToken } (설치형 앱)
 *    - POST /__mock/push/unsubscribe : { endpoint } 또는 { fcmToken } (로그아웃)
 *    - POST /__mock/push/last        : { endpoint } → 마지막 알림 { title, body, url, tag }
 */
import { createPrivateKey, generateKeyPairSync, sign } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const STORE_PATH = fileURLToPath(new URL('./.mock-push.json', import.meta.url))
/** 웹 푸시 구독 주소는 브라우저 푸시 서비스만 받음 (개발 서버가 아무 주소로나 요청을 보내지 않게) */
const PUSH_HOST = /(^|\.)(googleapis\.com|mozilla\.com|notify\.windows\.com|push\.apple\.com)$/
/** 설치형 앱 알림 채널 (담당자 앱 src/push.js CHANNEL_ID 와 같게) */
const ANDROID_CHANNEL = 'argos_alert'

const b64url = (buf) => Buffer.from(buf).toString('base64url')

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = ''
    req.on('data', (chunk) => (body += chunk))
    req.on('end', () => {
      try {
        resolve(JSON.parse(body || 'null'))
      } catch (e) {
        reject(e)
      }
    })
  })
}

/** VAPID 키 한 쌍: publicKey 는 브라우저가 쓰는 모양(04 + x + y, base64url), privateKey 는 PEM */
export function newKeys() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' })
  const { x, y } = publicKey.export({ format: 'jwk' })
  return {
    publicKey: b64url(Buffer.concat([Buffer.from([4]), Buffer.from(x, 'base64url'), Buffer.from(y, 'base64url')])),
    privateKey: privateKey.export({ format: 'pem', type: 'pkcs8' }),
  }
}

/** 키 + 구독 목록 (개발 서버를 다시 켜도 휴대폰이 다시 구독하지 않게 파일에 둠) */
function loadStore() {
  try {
    return JSON.parse(readFileSync(STORE_PATH, 'utf8'))
  } catch {
    return { ...newKeys(), subs: [] } // subs: [{ userId, endpoint } | { userId, fcmToken }]
  }
}

/** JWT 한 장 (header.claims.서명) */
function jwt(alg, claims, key, signOptions = {}) {
  const head = b64url(JSON.stringify({ typ: 'JWT', alg }))
  const body = b64url(JSON.stringify(claims))
  const sig = sign('sha256', Buffer.from(`${head}.${body}`), { key: createPrivateKey(key), ...signOptions })
  return `${head}.${body}.${b64url(sig)}`
}

/** VAPID 서명 (RFC 8292): 푸시 서비스(FCM)가 이 서버가 보낸 것인지 확인하는 값 */
export function vapidAuth(endpoint, keys) {
  const claims = { aud: new URL(endpoint).origin, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: 'mailto:argos-demo@example.com' }
  return `vapid t=${jwt('ES256', claims, keys.privateKey, { dsaEncoding: 'ieee-p1363' })}, k=${keys.publicKey}`
}

/** 서비스 계정으로 구글 접근 토큰 받을 때 내는 값 (RS256 JWT, FCM 보내기 권한만) */
export function googleAssertion(account, now = Math.floor(Date.now() / 1000)) {
  const claims = {
    iss: account.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: account.token_uri ?? 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }
  return jwt('RS256', claims, account.private_key)
}

/** 설치형 앱 알림 한 건 (FCM HTTP v1 messages:send 본문) */
export function fcmMessage(token, m) {
  return {
    message: {
      token,
      notification: { title: m.title, body: m.body },
      data: { type: m.type, checklistId: String(m.checklistId) }, // 앱 push.js 가 누르면 이 체크리스트로
      android: { priority: 'HIGH', notification: { channel_id: ANDROID_CHANNEL, tag: m.tag } },
    },
  }
}

/**
 * 예시 상황이 바뀐 것 중 담당자에게 알릴 일: [{ userId, type, checklistId, tag, url, title, body }]
 * - 앱 안 알림 띠(PhoneLayout)와 같은 기준(새 체크리스트 · 다시 알림) + 담당자 변경
 * - 개발 서버를 막 켰을 때(prev 없음), '처음 상황'처럼 진행 중으로 바로 바뀐 것은 알리지 않음
 * - 문구에 사람 이름 · 설비 이름 · 값은 넣지 않음 (설치형 앱 알림은 구글 서버를 거침)
 */
export function pushEvents(prev, next) {
  const c = next?.checklist
  const p = prev?.checklist
  if (!prev || !c || !['SENT', 'IN_PROGRESS'].includes(c.status)) return []
  const base = { userId: c.assigneeId, checklistId: c.id, tag: `checklist-${c.id}`, url: `#/checklists/${c.id}` }
  const count = `${c.doneTimes.length}/${c.items.length}`
  if (p?.id !== c.id) {
    if (c.status !== 'SENT') return []
    return [{ ...base, type: 'CHECKLIST_ASSIGNED', title: '새 점검 체크리스트가 도착했습니다', body: `항목 ${c.items.length}개 · 눌러서 확인하세요` }]
  }
  if (p.assigneeId !== c.assigneeId) {
    return [{ ...base, type: 'CHECKLIST_ASSIGNED', title: '점검 체크리스트가 배정되었습니다', body: `진행 ${count} · 눌러서 확인하세요` }]
  }
  if ((c.remindCount ?? 0) > (p.remindCount ?? 0)) {
    return [{ ...base, type: 'CHECKLIST_REMIND', title: '다시 알림 · 체크리스트를 확인해 주세요', body: `진행 ${count} · 눌러서 확인하세요` }]
  }
  return []
}

export function mockSync() {
  return {
    name: 'argos-mock-sync',
    apply: 'serve', // 개발 서버에서만 (vite build 때는 키 파일도 안 만듦)
    configureServer(server) {
      let state = null
      const clients = new Set()
      const store = loadStore()
      const last = {} // { endpoint: 마지막 알림 } — 서비스 워커가 받아 감
      const save = () => writeFileSync(STORE_PATH, JSON.stringify(store, null, 2))
      save()
      const log = (msg) => server.config.logger.info(`[push] ${msg}`, { timestamp: true })

      /* ───────── 설치형 앱 알림 (FCM HTTP v1) ───────── */
      let account = null
      const keyPath = process.env.ARGOS_FCM_KEY || join(homedir(), 'argos-fcm-key.json')
      if (existsSync(keyPath)) {
        try {
          account = JSON.parse(readFileSync(keyPath, 'utf8'))
          log(`FCM 서비스 계정 읽음 · 프로젝트 ${account.project_id}`)
        } catch (e) {
          log(`FCM 서비스 계정 키를 못 읽음 (${keyPath}): ${e.message}`)
        }
      } else if (process.env.ARGOS_FCM_KEY) {
        log(`ARGOS_FCM_KEY 파일이 없음: ${keyPath}`)
      }
      let access = null // { token, until }
      async function accessToken() {
        if (access && access.until > Date.now()) return access.token
        const res = await fetch(account.token_uri ?? 'https://oauth2.googleapis.com/token', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: googleAssertion(account) }),
        })
        const data = await res.json()
        if (!res.ok) throw new Error(`구글 접근 토큰 실패 ${res.status} ${data.error ?? ''}`)
        access = { token: data.access_token, until: Date.now() + (data.expires_in - 60) * 1000 }
        return access.token
      }

      /** 한 구독으로 보냄 → 응답 코드 (404·410: 앱·브라우저가 구독을 끊음) */
      async function sendTo(sub, m) {
        if (sub.fcmToken) {
          if (!account) return 'FCM 서비스 계정 키 없음 — 생략'
          const res = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${await accessToken()}`, 'Content-Type': 'application/json' },
            body: JSON.stringify(fcmMessage(sub.fcmToken, m)),
          })
          return res.status
        }
        last[sub.endpoint] = m
        const res = await fetch(sub.endpoint, {
          method: 'POST',
          headers: { TTL: '1800', Urgency: 'high', Authorization: vapidAuth(sub.endpoint, store) },
          body: new Uint8Array(0),
        })
        return res.status
      }

      async function notify(m) {
        for (const sub of store.subs.filter((s) => s.userId === m.userId)) {
          const kind = sub.fcmToken ? '앱(FCM)' : '브라우저'
          try {
            const status = await sendTo(sub, m)
            log(`${m.title} → 사용자 ${m.userId} ${kind} · 응답 ${status}`)
            if (status === 404 || status === 410) {
              store.subs = store.subs.filter((s) => s !== sub)
              save()
            }
          } catch (e) {
            log(`보내지 못함 → 사용자 ${m.userId} ${kind}: ${e.message}`)
          }
        }
      }

      // 설치형 앱(http://localhost)에서 오는 요청도 받음
      server.middlewares.use('/__mock', (req, res, next) => {
        res.setHeader('Access-Control-Allow-Origin', '*')
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        if (req.method !== 'OPTIONS') return next()
        res.statusCode = 204
        res.end()
      })

      server.middlewares.use('/__mock/push', async (req, res) => {
        const reply = (status, data) => {
          res.statusCode = status
          res.setHeader('Content-Type', 'application/json')
          res.end(JSON.stringify(data))
        }
        try {
          if (req.method === 'GET' && req.url === '/key') return reply(200, { publicKey: store.publicKey })
          if (req.method !== 'POST') return reply(405, { message: 'POST 만 받습니다' })
          const body = await readJson(req)
          const endpoint = typeof body?.endpoint === 'string' ? body.endpoint : ''
          const fcmToken = typeof body?.fcmToken === 'string' ? body.fcmToken : ''
          if (req.url === '/last') {
            const m = last[endpoint] ?? null
            log(`서비스 워커가 알림 문구를 받아 감 → 사용자 ${m?.userId ?? '?'}`)
            return reply(200, m)
          }
          if (req.url === '/subscribe') {
            if (!Number.isInteger(body?.userId)) return reply(400, { message: '사용자가 없습니다' })
            if (fcmToken) {
              // FCM 등록 토큰: 영문 · 숫자 · 기호 몇 개, 길이 수백 자
              if (!/^[\w:-]{20,4096}$/.test(fcmToken)) return reply(400, { message: 'FCM 토큰이 맞지 않습니다' })
              store.subs = [...store.subs.filter((s) => s.fcmToken !== fcmToken), { userId: body.userId, fcmToken }]
              save()
              log(`알림 켬 · 사용자 ${body.userId} (설치형 앱 FCM${account ? '' : ' — 서비스 계정 키가 없어 아직 못 보냄'})`)
              return reply(200, { ok: true })
            }
            const url = URL.canParse(endpoint) ? new URL(endpoint) : null
            if (!url || url.protocol !== 'https:' || !PUSH_HOST.test(url.hostname)) return reply(400, { message: '구독 정보가 맞지 않습니다' })
            store.subs = [...store.subs.filter((s) => s.endpoint !== endpoint), { userId: body.userId, endpoint }]
            save()
            log(`알림 켬 · 사용자 ${body.userId} (브라우저 ${url.hostname})`)
            return reply(200, { ok: true })
          }
          if (req.url === '/unsubscribe') {
            store.subs = store.subs.filter((s) => (fcmToken ? s.fcmToken !== fcmToken : s.endpoint !== endpoint))
            save()
            return reply(200, { ok: true })
          }
          reply(404, { message: '없는 주소' })
        } catch (e) {
          reply(400, { message: e.message })
        }
      })

      server.middlewares.use('/__mock/scenario', (req, res) => {
        if (req.method === 'GET' && req.url.startsWith('/stream')) {
          res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' })
          res.write(': open\n\n')
          clients.add(res)
          req.on('close', () => clients.delete(res))
          return
        }
        if (req.method === 'POST') {
          readJson(req)
            .then((msg) => {
              if (!msg?.state) throw new Error('state 없음')
              const prev = state
              state = msg.state
              const line = `data: ${JSON.stringify({ from: msg.from, state })}\n\n`
              for (const c of clients) c.write(line)
              res.statusCode = 204
              res.end()
              pushEvents(prev, state).forEach(notify)
            })
            .catch((e) => {
              res.statusCode = 400
              res.end(String(e.message))
            })
          return
        }
        res.setHeader('Content-Type', 'application/json')
        res.end(JSON.stringify({ state }))
      })
    },
  }
}
