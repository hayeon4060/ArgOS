// mockSync.js 확인: node --test mockSync.test.js (추가 설치 없음)
import assert from 'node:assert/strict'
import { createPublicKey, generateKeyPairSync, verify } from 'node:crypto'
import { test } from 'node:test'
import { fcmMessage, googleAssertion, newKeys, pushEvents, vapidAuth } from './mockSync.js'

const checklist = (over = {}) => ({
  id: 77,
  status: 'IN_PROGRESS',
  assigneeId: 21,
  sentBy: '한지우',
  items: ['a', 'b', 'c'],
  doneTimes: ['16:41:30'],
  remindCount: 0,
  ...over,
})

/** JWT 서명이 공개 키로 검증되는지 → 내용(claims) */
function verifyJwt(token, key, options = {}) {
  const [head, claims, sig] = token.split('.')
  assert.ok(verify('sha256', Buffer.from(`${head}.${claims}`), { key, ...options }, Buffer.from(sig, 'base64url')), '서명 검증')
  return JSON.parse(Buffer.from(claims, 'base64url'))
}

test('알릴 일: 새 체크리스트 · 다시 알림 · 담당자 변경만', () => {
  const before = { checklist: checklist() }
  assert.deepEqual(pushEvents(null, before), [], '개발 서버를 막 켰을 때는 알리지 않음')
  assert.deepEqual(pushEvents(before, { checklist: checklist({ doneTimes: ['1', '2'] }) }), [], '항목 체크는 알리지 않음')
  assert.deepEqual(pushEvents({ checklist: checklist({ id: 78 }) }, before), [], "'처음 상황'(진행 중으로 바로)은 알리지 않음")

  const [sent] = pushEvents(before, { checklist: checklist({ id: 78, status: 'SENT', doneTimes: [] }) })
  assert.equal(sent.userId, 21)
  assert.equal(sent.checklistId, 78)
  assert.equal(sent.type, 'CHECKLIST_ASSIGNED')
  assert.equal(sent.url, '#/checklists/78')
  assert.ok(!`${sent.title}${sent.body}`.includes('한지우'), '구글 서버를 거치는 문구에 사람 이름을 넣지 않음')

  const [remind] = pushEvents(before, { checklist: checklist({ remindCount: 1 }) })
  assert.equal(remind.type, 'CHECKLIST_REMIND')
  assert.match(remind.body, /1\/3/)

  const [moved] = pushEvents(before, { checklist: checklist({ assigneeId: 22 }) })
  assert.equal(moved.userId, 22, '바뀐 담당자에게')

  assert.deepEqual(pushEvents(before, { checklist: checklist({ status: 'REPORTED', remindCount: 1 }) }), [], '보고한 뒤에는 알리지 않음')
})

test('웹 푸시 VAPID 서명: 공개 키로 검증되고 aud 는 푸시 서비스 주소', () => {
  const keys = newKeys()
  const [, token, k] = vapidAuth('https://fcm.googleapis.com/wp/abc', keys).match(/^vapid t=([^,]+), k=(.+)$/)
  assert.equal(k, keys.publicKey)
  const raw = Buffer.from(keys.publicKey, 'base64url') // 04 + x(32) + y(32)
  assert.equal(raw.length, 65)
  const key = createPublicKey({
    key: { kty: 'EC', crv: 'P-256', x: raw.subarray(1, 33).toString('base64url'), y: raw.subarray(33).toString('base64url') },
    format: 'jwk',
  })
  assert.equal(verifyJwt(token, key, { dsaEncoding: 'ieee-p1363' }).aud, 'https://fcm.googleapis.com')
})

test('설치형 앱 FCM: 서비스 계정 서명 · 메시지 모양', () => {
  const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  const account = { client_email: 'demo@argos.iam.gserviceaccount.com', private_key: privateKey.export({ format: 'pem', type: 'pkcs8' }) }
  const claims = verifyJwt(googleAssertion(account, 1000), publicKey)
  assert.equal(claims.iss, account.client_email)
  assert.equal(claims.scope, 'https://www.googleapis.com/auth/firebase.messaging')
  assert.equal(claims.exp - claims.iat, 3600)

  const [m] = pushEvents({ checklist: checklist() }, { checklist: checklist({ remindCount: 1 }) })
  const { message } = fcmMessage('token-1', m)
  assert.equal(message.token, 'token-1')
  assert.deepEqual(message.data, { type: 'CHECKLIST_REMIND', checklistId: '77' }, 'data 는 문자열만')
  assert.equal(message.android.priority, 'HIGH', '화면이 꺼져 있어도 바로')
  assert.equal(message.android.notification.channel_id, 'argos_alert')
})
