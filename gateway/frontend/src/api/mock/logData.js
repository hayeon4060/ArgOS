/**
 * 이용 로그 예시 데이터 (화면설계안 v-rev3.js LOGS · vLogs(), API 명세서 G2 GET /logs · /logs/export)
 *
 * - 백엔드에는 이용 로그를 쓰는 곳(common/ActivityLogger: SHUTDOWN · FAN · CHECKLIST · THRESHOLD · LAYOUT · EXPORT)만 있고
 *   읽는 API(/logs)는 아직 없어 명세서 G2 응답 모양을 따름
 * - 사용자는 사용자 관리 예시 데이터(userData.js)와 같은 사람들, 최근 30일치를 정해진 순서(씨앗 난수)로 만듦
 * - 보이는 범위 (화면설계서 G2): 본사 관리자는 전체, 공장 담당자는 소속 공장 기록만 (본사 관리자의 기록은 안 보임)
 * - 'CSV 내려받기'를 하면 그것도 로그에 남음 (명세서)
 * - 상태는 이 파일 안에만 있음 (새로고침하면 처음으로)
 */
import { mockSiteTrees } from './data'
import { logUsers } from './userData'

const ROLE_LABEL = { HQ_ADMIN: '본사 관리자', PLANT_ADMIN: '공장 담당자', WORKER: '공정 담당자' }
const pad = (n) => String(n).padStart(2, '0')

/** Date → '2026-11-17T08:41:05+09:00' (다른 예시 데이터처럼 PC 시각을 한국 시각으로 봄) */
const iso = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}+09:00`

/** 공장 id → 공정 [{ id, name }] */
const processesOf = (factoryId) => mockSiteTrees()[1].factories.find((f) => f.id === factoryId)?.processes ?? []
/** 5공장 도장 공정 id (관리 조치는 대부분 배치도가 있는 도장 공정) */
const paintId = () => processesOf(5).find((p) => p.name.startsWith('도장'))?.id ?? null

/* ───────── 내용 문구 (설계안 LOGS 의 M + 명세서 예시) ───────── */
const EQUIP = ['상도 건조로', '중도 건조로', '전착 건조로', '전처리', '공조기 (AHU)', '소각설비', '도료 혼합실']
const RULES = [
  ['CT-17', '전류', 'A', 22.5, 21.6, '상도 건조로'],
  ['TE-15', '온도', '℃', 185, 180, '상도 건조로'],
  ['CO-13', 'CO', 'ppm', 35, 30, '전착 건조로'],
  ['CT-14', '전류', 'A', 19.2, 18.8, '중도 건조로'],
]
const WORKERS = ['김도현', '박성훈', '이재호']
const FORMS = ['F01 밀폐공간 작업허가서', 'F02 환기·배기설비 점검일지', 'F04 가스 농도 측정 기록', 'F05 안전교육 일지']

/** 유형별 내용 만들기 → { message, before, after } */
const MAKE = {
  LOGIN: (u, r) => ({ message: u.role === 'WORKER' ? '로그인 (모바일 앱)' : `로그인 (PC, 10.21.4.${10 + Math.floor(r() * 80)})` }),
  LOGOUT: () => ({ message: '로그아웃' }),
  CHECKLIST: (u, r) => {
    if (u.role === 'WORKER') return { message: `체크리스트 ${3 + Math.floor(r() * 3)}개 항목 완료` }
    const eq = EQUIP[Math.floor(r() * 3)]
    const list = [
      `주의 확인 · ${eq} 배기팬`,
      `체크리스트 전송 · ${eq} → ${WORKERS[Math.floor(r() * 3)]}`,
      `조치 확인 · 전류 주의 해제 · ${eq}`,
      `체크리스트 담당자 변경 · ${WORKERS[0]} → ${WORKERS[1]}`,
    ]
    return { message: list[Math.floor(r() * list.length)] }
  },
  FAN: (u, r) => {
    const a = 1100 + Math.floor(r() * 4) * 40
    return { message: `배기팬 회전수 ${a.toLocaleString()} → ${(a + 120).toLocaleString()} rpm · ${EQUIP[Math.floor(r() * 3)]}`, before: { rpm: a }, after: { rpm: a + 120 } }
  },
  SHUTDOWN: (u, r) => ({ message: `설비 셧다운 · ${EQUIP[Math.floor(r() * 3)]}` }),
  FIRE_CLEAR: () => ({ message: '화재 경보 해제 · 비밀번호 확인' }),
  THRESHOLD: (u, r) => {
    const [tag, label, unit, a, b, eq] = RULES[Math.floor(r() * RULES.length)]
    return { message: `${tag} ${label} 주의 기준 ${a} → ${b} ${unit} (겨울) · ${eq}`, before: { warn: a }, after: { warn: b } }
  },
  LAYOUT: (u, r) => {
    const eq = EQUIP[3 + Math.floor(r() * 4)]
    return { message: r() < 0.6 ? `설비 위치 이동 · ${eq}` : `설비 사용 상태 변경 · ${eq} 사용 안 함` }
  },
  USER: (u, r) => {
    const list = ['사용자 추가 · u5_paint04', '권한 변경 · 공정 담당자 → 공장 담당자', '담당 공정 변경 · 박성훈 (도장 → 도장, 의장)', '비밀번호 초기화 · u5_press02']
    return { message: list[Math.floor(r() * list.length)] }
  },
  INSPECTION: (u, r) => ({ message: `서류 등록 · ${FORMS[Math.floor(r() * FORMS.length)]}` }),
  EXPORT: (u, r) => ({ message: r() < 0.6 ? '알림 이력 CSV 내려받기' : '월별 보고서 내려받기 · 2026-10' }),
}

/** 권한별로 자주 하는 일 (같은 값이 여러 번 = 더 자주) */
const TYPES_BY_ROLE = {
  WORKER: ['LOGIN', 'LOGIN', 'LOGOUT', 'CHECKLIST', 'CHECKLIST', 'INSPECTION'],
  PLANT_ADMIN: ['LOGIN', 'LOGIN', 'LOGOUT', 'CHECKLIST', 'CHECKLIST', 'CHECKLIST', 'THRESHOLD', 'FAN', 'LAYOUT', 'USER', 'EXPORT', 'SHUTDOWN'],
  HQ_ADMIN: ['LOGIN', 'LOGOUT', 'THRESHOLD', 'USER', 'EXPORT'],
}

/* ───────── 최근 30일치 만들기 (처음 한 번) ───────── */
let LOGS = null
let nextId = 90001

function build() {
  // 씨앗 난수: 새로고침해도 같은 순서 (설계안 R())
  let x = 7
  const r = () => {
    x = (x * 16807) % 2147483647
    return x / 2147483647
  }
  const now = new Date()
  const workers = logUsers().filter((u) => u.role === 'WORKER' && u.factoryId)
  const admins = logUsers().filter((u) => u.role !== 'WORKER')
  const list = []
  for (let i = 0; i < 360; i++) {
    // 관리자 일(기준값 · 셧다운 · 사용자 …)이 고루 보이게 관리자를 절반쯤 뽑음 (공정 담당자가 훨씬 많아서)
    const pool = r() < 0.5 ? admins : workers
    const u = pool[Math.floor(r() * pool.length)]
    const types = TYPES_BY_ROLE[u.role]
    const type = types[Math.floor(r() * types.length)]
    // 낮 시간(7~18시)에 많이
    const hour = r() < 0.82 ? 7 + Math.floor(r() * 12) : Math.floor(r() * 24)
    const d = new Date(now)
    d.setDate(d.getDate() - Math.floor(r() * 30))
    d.setHours(hour, Math.floor(r() * 60), Math.floor(r() * 60), 0)
    if (d > now) continue // 오늘 아직 안 온 시각은 빼기
    const admin = u.role !== 'WORKER'
    const factoryId = u.factoryId ?? (u.role === 'HQ_ADMIN' && type !== 'LOGIN' && type !== 'LOGOUT' ? 5 : null)
    const processId = admin ? (factoryId === 5 && type !== 'LOGIN' && type !== 'LOGOUT' && type !== 'USER' ? paintId() : null) : u.processId
    list.push(row(u, type, d, factoryId, processId, MAKE[type](u, r)))
  }
  list.sort((a, b) => b.at.localeCompare(a.at))
  // 번호는 시각 순서대로 (오래된 것이 작은 번호)
  list.forEach((item, i) => (item.id = 90000 + list.length - i))
  nextId = 90001 + list.length
  return list
}

/** 로그 한 줄 (명세서 G2 items 모양) */
function row(u, type, date, factoryId, processId, { message, before = null, after = null }) {
  return {
    id: nextId++,
    at: iso(date),
    loginId: u.loginId,
    name: u.name,
    role: u.role,
    scopeText: u.scopeText,
    factoryId,
    processId,
    type,
    message,
    before,
    after,
  }
}

const all = () => (LOGS ??= build())

/** 지금 로그인한 사람의 로그인 기록 (설계안: 표 맨 위 '로그인') — 처음 볼 때 한 번 */
const loggedIn = new Set()
function ensureLogin(actor) {
  if (!actor || loggedIn.has(actor.loginId)) return
  loggedIn.add(actor.loginId)
  const u = logUsers().find((x) => x.loginId === actor.loginId)
  if (!u) return
  const d = new Date(Date.now() - 3 * 60_000)
  all().unshift(row(u, 'LOGIN', d, u.factoryId, null, { message: '로그인 (PC, 10.21.4.17)' }))
}

/** 보이는 범위: 공장 담당자는 소속 공장 기록만, 본사 관리자 기록은 안 보임 */
function canSee(actor, r) {
  if (actor?.role === 'HQ_ADMIN') return true
  const mine = actor?.scope?.factoryIds ?? []
  return r.role !== 'HQ_ADMIN' && mine.includes(r.factoryId)
}

/** 조건으로 거르기 (유형 빼고) */
function filtered(actor, c) {
  ensureLogin(actor)
  const q = (c.q ?? '').trim().toLowerCase()
  return all().filter(
    (r) =>
      canSee(actor, r) &&
      r.at.slice(0, 10) >= c.from &&
      r.at.slice(0, 10) <= c.to &&
      (!c.factoryId || r.factoryId === Number(c.factoryId)) &&
      (!c.processId || r.processId === Number(c.processId)) &&
      (!q || r.loginId.toLowerCase().includes(q) || r.name.includes(q) || r.message.toLowerCase().includes(q)),
  )
}

/** type: 'LOGIN,LOGOUT' 처럼 여러 개 (쉼표) */
const typeFilter = (c) => (c.type ? (r) => c.type.split(',').includes(r.type) : () => true)

/**
 * GET /logs
 * @param {{ from, to, factoryId?, processId?, type?, q?, page, size }} c
 * @returns {{ items, countsByType, byHour, topUsers, page, size, total }}
 */
export function listLogs(actor, c) {
  const scoped = filtered(actor, c)
  // 유형별 건수는 유형 조건을 빼고 셈 → 다른 유형 칸을 눌러 바꿀 수 있게 (명세서에 기준이 없어 가정)
  const countsByType = {}
  for (const r of scoped) countsByType[r.type] = (countsByType[r.type] ?? 0) + 1
  const rows = scoped.filter(typeFilter(c))
  const byHour = Array.from({ length: 24 }, (_, hour) => ({ hour, count: 0 }))
  for (const r of rows) byHour[Number(r.at.slice(11, 13))].count++
  // 많이 쓴 사용자 (명세서에 없음 — 설계안 시간대별 이용 카드 아래)
  const by = {}
  for (const r of rows) by[r.loginId] = { loginId: r.loginId, name: r.name, count: (by[r.loginId]?.count ?? 0) + 1 }
  const topUsers = Object.values(by)
    .sort((a, b) => b.count - a.count)
    .slice(0, 4)
  const page = c.page ?? 0
  const size = c.size ?? 20
  return { items: rows.slice(page * size, (page + 1) * size), countsByType, byHour, topUsers, page, size, total: rows.length }
}

/* ───────── GET /logs/export ───────── */
const csvCell = (v) => {
  const s = v == null ? '' : String(v)
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
const TYPE_LABEL = {
  LOGIN: '로그인', LOGOUT: '로그아웃', CHECKLIST: '조치', FAN: '배기팬 조작', SHUTDOWN: '셧다운', FIRE_CLEAR: '화재 해제',
  THRESHOLD: '기준값 변경', LAYOUT: '설비 수정', USER: '사용자', INSPECTION: '서류 등록', EXPORT: '내려받기',
}

/**
 * CSV 만들기 (화면설계서 G2 'CSV 구성' 중 있는 열) + 내려받은 것도 로그에 남김
 * @returns {{ text, count }}
 */
export function exportLogs(actor, c) {
  const rows = filtered(actor, c).filter(typeFilter(c))
  const head = ['id', '시각', '아이디', '이름', '권한', '담당', '종류', '내용', '이전 값', '이후 값']
  const lines = rows.map((r) =>
    [
      r.id, `${r.at.slice(0, 10)} ${r.at.slice(11, 19)}`, r.loginId, r.name, ROLE_LABEL[r.role], r.scopeText, TYPE_LABEL[r.type] ?? r.type, r.message,
      r.before ? JSON.stringify(r.before) : '', r.after ? JSON.stringify(r.after) : '',
    ]
      .map(csvCell)
      .join(','),
  )
  const u = logUsers().find((x) => x.loginId === actor?.loginId)
  if (u) all().unshift(row(u, 'EXPORT', new Date(), u.factoryId, null, { message: `이용 로그 CSV 내려받기 (${rows.length}건)` }))
  // 엑셀에서 한글이 깨지지 않게 BOM (화면설계서)
  return { text: `﻿${[head.join(','), ...lines].join('\n')}`, count: rows.length }
}
