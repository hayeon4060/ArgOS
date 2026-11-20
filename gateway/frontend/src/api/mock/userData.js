/**
 * 사용자 관리 예시 데이터 (화면설계안 v-rev3.js vUsers() 의 HQU · UDB, API 명세서 G1)
 *
 * - 백엔드에는 /me · /me/password 만 있고 /users 컨트롤러가 아직 없어 명세서 G1 모양을 따름
 * - 권한 범위 (화면설계서 G1 '권한별로 할 수 있는 범위')
 *   · 본사 관리자: 모든 사용자 조회·등록·수정·삭제 (본사 관리자 권한은 본사 관리자만 줄 수 있음)
 *   · 공장 담당자: 소속 공장의 공장 담당자·공정 담당자만 (본사 관리자로 올릴 수 없음)
 *   · 자기 자신은 권한 변경·정지·삭제 불가
 * - 상태는 이 파일 안에만 있음 (새로고침하면 처음으로)
 */
import { MOCK_PASSWORD, mockSiteTrees } from './data'
import { activeChecklist } from './scenario'

const ROLE_LABEL = { HQ_ADMIN: '본사 관리자', PLANT_ADMIN: '공장 담당자', WORKER: '공정 담당자' }
const FACTORY_ID = { '1공장': 1, '2공장': 2, '3공장': 3, '4공장': 4, '5공장': 5, '엔진·변속기 공장': 6 }

/** 공장 id → { name, processes: [{ id, name }] } (사업장 트리에서) */
function factoryOf(id) {
  return mockSiteTrees()[1].factories.find((f) => f.id === Number(id))
}
/** '도장' → 그 공장의 '도장 공정' id */
function processIdOf(factoryId, short) {
  return factoryOf(factoryId)?.processes.find((p) => p.name.startsWith(short))?.id ?? null
}

/** 담당 범위 문구: 모든 사업장 / 5공장 전체 / 5공장 · 도장 공정 */
function scopeText(u) {
  if (u.role === 'HQ_ADMIN') return '모든 사업장'
  const f = factoryOf(u.scope.factoryIds[0])
  if (!f) return '공장 미배정'
  if (u.role === 'PLANT_ADMIN') return `${f.name} 전체`
  const procs = f.processes.filter((p) => u.scope.processIds.includes(p.id)).map((p) => p.name)
  return `${f.name} · ${procs.join(', ') || '공정 미배정'}`
}

/* ───────── 처음 사용자 (설계안 HQU · UDB) ───────── */
// [id, 이름, 아이디, 권한, 공장, [공정], 최근 접속('MM-DD HH:mm'), 정지]
const SEED = [
  [1, '서지훈', 'hq_admin', 'HQ_ADMIN', null, [], '11-17 08:02'],
  [2, '민채원', 'hq_admin02', 'HQ_ADMIN', null, [], '11-16 11:40'],
  [7, '한지우', 'ulsan5_admin', 'PLANT_ADMIN', '5공장', [], '11-17 08:10'],
  [8, '윤서연', 'ulsan_admin02', 'PLANT_ADMIN', '5공장', [], '11-16 15:20'],
  [9, '오승민', 'ulsan_admin03', 'PLANT_ADMIN', '5공장', [], '11-15 18:02'],
  [21, '김도현', 'u5_paint01', 'WORKER', '5공장', ['도장'], '11-17 08:41'],
  [22, '박성훈', 'u5_paint02', 'WORKER', '5공장', ['도장'], '11-17 07:58'],
  [23, '이재호', 'u5_paint03', 'WORKER', '5공장', ['도장'], '11-16 12:30'],
  [31, '최민규', 'u5_press01', 'WORKER', '5공장', ['프레스'], '11-16 16:02'],
  [32, '신예준', 'u5_press02', 'WORKER', '5공장', ['프레스'], '11-16 09:15'],
  [33, '정우진', 'u5_body01', 'WORKER', '5공장', ['차체'], '11-16 15:47'],
  [34, '강민서', 'u5_body02', 'WORKER', '5공장', ['차체'], '11-15 17:30'],
  [35, '조현우', 'u5_assy01', 'WORKER', '5공장', ['의장'], '11-16 13:11'],
  [36, '임수빈', 'u5_assy02', 'WORKER', '5공장', ['의장'], '10-28 08:40', true],
  [37, '배준영', 'u4_paint01', 'WORKER', '4공장', ['도장'], '11-16 16:20'],
  [38, '문지호', 'u3_body01', 'WORKER', '3공장', ['차체'], '11-16 11:05'],
  [39, '서하윤', 'u1_press01', 'WORKER', '1공장', ['프레스'], '11-16 10:36'],
  [40, '권태윤', 'u4_body01', 'WORKER', '4공장', ['차체'], '11-16 15:02'],
  [41, '홍유진', 'u4_assy01', 'WORKER', '4공장', ['의장'], '11-16 13:44'],
  [42, '장하준', 'u3_paint01', 'WORKER', '3공장', ['도장'], '11-16 16:11'],
  [43, '남지안', 'u3_press01', 'WORKER', '3공장', ['프레스'], '11-16 08:52'],
  [44, '유승현', 'u2_body01', 'WORKER', '2공장', ['차체'], '11-16 14:27'],
  [45, '백서준', 'u2_paint01', 'WORKER', '2공장', ['도장'], '11-16 12:03'],
  [46, '전하린', 'u2_assy01', 'WORKER', '2공장', ['의장'], '11-15 19:20'],
  [47, '송민재', 'u1_body01', 'WORKER', '1공장', ['차체'], '11-16 15:38'],
  [48, '안도윤', 'u1_paint01', 'WORKER', '1공장', ['도장'], '11-16 11:49'],
  [49, '황시우', 'ue_mach01', 'WORKER', '엔진·변속기 공장', ['가공'], '11-16 16:05'],
  [50, '노은서', 'ue_assy01', 'WORKER', '엔진·변속기 공장', ['조립'], '11-16 10:18'],
  [51, '차준호', 'ue_mach02', 'WORKER', '엔진·변속기 공장', ['가공'], '11-14 17:56'],
]

const year = new Date().getFullYear()
const isoOf = (mmddhhmm) => `${year}-${mmddhhmm.slice(0, 5)}T${mmddhhmm.slice(6)}:00+09:00`

const USERS = SEED.map(([id, name, loginId, role, factory, procs, last, stopped], i) => {
  const factoryId = FACTORY_ID[factory] ?? null
  return {
    id,
    name,
    loginId,
    role,
    phone: role === 'HQ_ADMIN' ? `02-3464-${1100 + i * 17}` : `010-${String(2000 + ((i * 7919) % 7000)).padStart(4, '0')}-${String(1000 + ((i * 4877) % 8999)).padStart(4, '0')}`,
    active: !stopped,
    scope: {
      siteId: role === 'HQ_ADMIN' ? null : 1,
      factoryIds: factoryId ? [factoryId] : [],
      processIds: procs.map((p) => processIdOf(factoryId, p)).filter(Boolean),
    },
    createdAt: role === 'HQ_ADMIN' ? `${year}-09-01T09:00:00+09:00` : `${year}-10-05T09:00:00+09:00`,
    lastLoginAt: isoOf(last),
    pushReady: role === 'WORKER',
    history: [
      { at: `${year}-10-05T09:00:00+09:00`, by: '한지우', text: `${ROLE_LABEL[role]}로 지정` },
      { at: `${year}-09-01T09:00:00+09:00`, by: role === 'HQ_ADMIN' ? '시스템' : '윤서연', text: '계정 등록' },
    ],
  }
})
let nextId = 60
let myPassword = MOCK_PASSWORD

const find = (id) => USERS.find((u) => u.id === Number(id))
const copy = (v) => JSON.parse(JSON.stringify(v))
const nowIso = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}+09:00`
}
const fail = (code, message, status) => ({ error: { code, message, status } })

/* ───────── 권한 범위 ───────── */
/** 이 사람이 볼 수 있는 사용자인가 (본사: 전체 / 공장 담당자: 소속 공장의 공장·공정 담당자) */
function canSee(actor, u) {
  if (actor.role === 'HQ_ADMIN') return true
  if (actor.role !== 'PLANT_ADMIN') return u.id === actor.id
  return u.role !== 'HQ_ADMIN' && u.scope.factoryIds.some((f) => actor.scope.factoryIds.includes(f))
}
/** 줄 수 있는 권한 */
const rolesFor = (actor) => (actor.role === 'HQ_ADMIN' ? ['HQ_ADMIN', 'PLANT_ADMIN', 'WORKER'] : ['PLANT_ADMIN', 'WORKER'])

/** 사용 정지 · 삭제 가능 여부 (진행 중 체크리스트 · 마지막 공장 담당자) */
function blockReason(u) {
  const ck = activeChecklist()
  if (ck && ck.assigneeId === u.id) return fail('HAS_ACTIVE_CHECKLIST', '진행 중인 체크리스트 1건이 있습니다. 담당자를 바꾼 뒤 처리하세요.', 409)
  if (u.role === 'PLANT_ADMIN') {
    const same = USERS.filter((x) => x.role === 'PLANT_ADMIN' && x.active && x.id !== u.id && x.scope.factoryIds[0] === u.scope.factoryIds[0])
    if (!same.length) return fail('LAST_PLANT_ADMIN', '이 공장에 공장 담당자가 한 명은 있어야 합니다.', 409)
  }
  return null
}

/* ───────── API ───────── */
const rowOf = (u) => ({
  id: u.id,
  name: u.name,
  loginId: u.loginId,
  role: u.role,
  scopeText: scopeText(u),
  factoryId: u.scope.factoryIds[0] ?? null,
  lastLoginAt: u.lastLoginAt,
  active: u.active,
})

/** GET /users?role=&factoryId=&active=&q=&page=&size= */
/** 이용 로그 예시 데이터용 사용자 (api/mock/logData.js) — 아이디 · 이름 · 권한 · 담당 공장·공정 · 담당 문구 */
export const logUsers = () =>
  USERS.map((u) => ({
    id: u.id,
    name: u.name,
    loginId: u.loginId,
    role: u.role,
    factoryId: u.scope.factoryIds[0] ?? null,
    processId: u.scope.processIds[0] ?? null,
    scopeText: scopeText(u),
  }))

export function list(actor, { role, factoryId, active, q, page = 0, size = 15 } = {}) {
  const seen = USERS.filter((u) => canSee(actor, u))
  const rows = seen.filter(
    (u) =>
      (!role || u.role === role) &&
      // 공장 고르기: 그 공장 사람 + 본사 관리자(공장이 없음)는 빼고
      (!factoryId || u.scope.factoryIds.includes(Number(factoryId))) &&
      (active === undefined || active === '' || u.active === (active === true || active === 'true')) &&
      (!q || u.name.includes(q) || u.loginId.toLowerCase().includes(String(q).toLowerCase())),
  )
  const counts = { HQ_ADMIN: 0, PLANT_ADMIN: 0, WORKER: 0 }
  seen.forEach((u) => (counts[u.role] += 1))
  return {
    counts,
    // 추가 필드: 사용 정지 인원 (왼쪽 '사용 정지 n명')
    inactiveCount: seen.filter((u) => !u.active).length,
    items: rows.slice(page * size, page * size + size).map(rowOf),
    page,
    size,
    total: rows.length,
  }
}

/** GET /users/{id} */
export function detail(actor, id) {
  const u = find(id)
  if (!u) return fail('NOT_FOUND', '없는 사용자입니다.', 404)
  if (!canSee(actor, u)) return fail('OUT_OF_SCOPE', '다른 공장 사용자는 볼 수 없습니다.', 403)
  return { data: { ...copy(u), scopeText: scopeText(u) } }
}

/** GET /users/check-login-id?loginId= */
export const checkLoginId = (loginId) => ({ available: !USERS.some((u) => u.loginId === loginId) })

/** 검사 (등록·수정 공통) */
function validate(actor, body, target) {
  if (!rolesFor(actor).includes(body.role)) return fail('ROLE_NOT_ALLOWED', '줄 수 없는 권한입니다.', 403)
  if (body.role === 'WORKER' && !body.scope?.processIds?.length) return fail('SCOPE_REQUIRED', '공정을 한 개 이상 선택해 주세요.', 400)
  if (body.role !== 'HQ_ADMIN' && !body.scope?.factoryIds?.length) return fail('SCOPE_REQUIRED', '공장을 선택해 주세요.', 400)
  if (actor.role === 'PLANT_ADMIN' && !body.scope.factoryIds.every((f) => actor.scope.factoryIds.includes(f))) {
    return fail('OUT_OF_SCOPE', '소속 공장 사용자만 다룰 수 있습니다.', 403)
  }
  if (target && target.id === actor.id && target.role !== body.role) return fail('ROLE_NOT_ALLOWED', '내 권한은 직접 바꿀 수 없습니다.', 403)
  return null
}
const scopeOf = (body) => ({
  siteId: body.role === 'HQ_ADMIN' ? null : 1,
  factoryIds: body.role === 'HQ_ADMIN' ? [] : body.scope.factoryIds.map(Number),
  processIds: body.role === 'WORKER' ? body.scope.processIds.map(Number) : [],
})
/** 임시 비밀번호 (예: Arg-7Q2k9) */
const tempPassword = () => `Arg-${Math.random().toString(36).slice(2, 7)}`

/** POST /users → { id, tempPassword } */
export function create(actor, body) {
  if (!/^[a-z0-9_]{4,20}$/.test(body.loginId ?? '')) return fail('VALIDATION_FAILED', '아이디는 영문 소문자, 숫자, 밑줄로 4~20자입니다.', 400)
  if (!checkLoginId(body.loginId).available) return fail('LOGIN_ID_TAKEN', '이미 쓰는 아이디입니다.', 409)
  const bad = validate(actor, body)
  if (bad) return bad
  const u = {
    id: nextId++,
    name: body.name.trim(),
    loginId: body.loginId,
    role: body.role,
    phone: body.phone ?? '',
    active: true,
    scope: scopeOf(body),
    createdAt: nowIso(),
    lastLoginAt: null,
    pushReady: false,
    history: [{ at: nowIso(), by: actor.name, text: '계정 등록' }],
  }
  USERS.unshift(u)
  return { data: { id: u.id, tempPassword: tempPassword() } }
}

/** PUT /users/{id} → { id, role, scopeText } */
export function update(actor, id, body) {
  const u = find(id)
  if (!u) return fail('NOT_FOUND', '없는 사용자입니다.', 404)
  if (!canSee(actor, u)) return fail('OUT_OF_SCOPE', '다른 공장 사용자는 고칠 수 없습니다.', 403)
  const bad = validate(actor, body, u)
  if (bad) return bad
  const next = scopeOf(body)
  // 진행 중 체크리스트가 있는 공정 담당자는 공정을 뺄 수 없음 (명세서 409)
  const ck = activeChecklist()
  if (ck && ck.assigneeId === u.id && u.scope.processIds.some((p) => !next.processIds.includes(p))) {
    return fail('HAS_ACTIVE_CHECKLIST', '진행 중인 체크리스트가 있어 공정을 뺄 수 없습니다.', 409)
  }
  const text =
    u.role !== body.role ? `${ROLE_LABEL[body.role]}로 변경` : JSON.stringify(u.scope) !== JSON.stringify(next) ? '담당 범위 변경' : '정보 수정'
  Object.assign(u, { name: body.name.trim(), phone: body.phone ?? u.phone, role: body.role, scope: next })
  u.history.unshift({ at: nowIso(), by: actor.name, text })
  return { data: { id: u.id, role: u.role, scopeText: scopeText(u) } }
}

/** PATCH /users/{id}/status { active } */
export function setStatus(actor, id, active) {
  const u = find(id)
  if (!u) return fail('NOT_FOUND', '없는 사용자입니다.', 404)
  if (u.id === actor.id) return fail('CANNOT_DISABLE_SELF', '자기 자신은 정지할 수 없습니다.', 409)
  if (!canSee(actor, u)) return fail('OUT_OF_SCOPE', '다른 공장 사용자는 고칠 수 없습니다.', 403)
  if (!active) {
    const bad = blockReason(u)
    if (bad) return bad
  }
  u.active = active
  u.history.unshift({ at: nowIso(), by: actor.name, text: active ? '사용 재개' : '사용 정지' })
  return { data: { id: u.id, active } }
}

/** DELETE /users/{id} */
export function remove(actor, id) {
  const u = find(id)
  if (!u) return fail('NOT_FOUND', '없는 사용자입니다.', 404)
  if (u.id === actor.id) return fail('CANNOT_DELETE_SELF', '자기 자신은 삭제할 수 없습니다.', 409)
  if (!canSee(actor, u)) return fail('OUT_OF_SCOPE', '다른 공장 사용자는 삭제할 수 없습니다.', 403)
  const bad = blockReason(u)
  if (bad) return bad
  USERS.splice(USERS.indexOf(u), 1)
  return { data: null }
}

/** POST /users/{id}/reset-password → { tempPassword } */
export function resetPassword(actor, id) {
  const u = find(id)
  if (!u) return fail('NOT_FOUND', '없는 사용자입니다.', 404)
  if (!canSee(actor, u)) return fail('OUT_OF_SCOPE', '다른 공장 사용자입니다.', 403)
  u.history.unshift({ at: nowIso(), by: actor.name, text: '비밀번호 초기화' })
  return { data: { tempPassword: tempPassword() } }
}

/* ───────── 내 정보 (백엔드 GET /me · PUT /me/password 있음, PUT /me 는 명세서만) ───────── */
/** GET /me (백엔드 MeView 와 같은 모양) */
export function me(actor) {
  const u = find(actor.id)
  if (!u) return fail('AUTH_REQUIRED', '다시 로그인해 주세요.', 401)
  const { id, loginId, name, phone, role, scope, lastLoginAt } = u
  return { data: { id, loginId, name, phone, role, scope: copy(scope), pwResetRequired: false, lastLoginAt } }
}

/** PUT /me { name, phone } */
export function updateMe(actor, { name, phone }) {
  const u = find(actor.id)
  if (!u) return fail('AUTH_REQUIRED', '다시 로그인해 주세요.', 401)
  Object.assign(u, { name: name.trim(), phone })
  u.history.unshift({ at: nowIso(), by: u.name, text: '내 정보 수정' })
  return { data: { id: u.id, name: u.name, phone: u.phone } }
}

/** PUT /me/password { currentPassword, newPassword } (백엔드 규칙: 8자 이상, 영문·숫자·특수문자) */
export function changePassword(actor, { currentPassword, newPassword }) {
  // 백엔드는 지금 비밀번호가 틀리면 401 — 예시는 같은 코드로 돌려줌 (backend-notes 14장: 401 이면 로그아웃되는 문제)
  if (currentPassword !== myPassword) return fail('PASSWORD_MISMATCH', '현재 비밀번호가 틀렸습니다.', 401)
  if (!/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/.test(newPassword ?? '')) {
    return fail('PASSWORD_RULE', '비밀번호는 8자 이상이며 영문·숫자·특수문자를 포함해야 합니다.', 400)
  }
  myPassword = newPassword
  return { data: null }
}
