/**
 * 예시 상황 — 경보(3단계) · 화재 · 셧다운 (scenario.js 의 상태를 바꾸는 함수들)
 * - 화면설계안 js/shell.js raise('dng' | 'fire'), shutdownSeq(), afterShutdown(), afterFire() 와 같은 흐름
 * - 경보: 16:47:36 전류 경보 → 16:50:12 온도 주의 → 16:52:08 CO 경보 (상도 건조로 27.5 A · 164 ℃ · 34 ppm)
 *   그 전의 주의(1042)는 체크리스트 5개를 모두 마친 상태
 * - 셧다운: 버너 연료 차단(1.3초) → 컨베이어 정지(1.3초) → 배기팬 추가 가동 후 정지(2.4초, 실제는 5분)
 *   실제 서버에서는 명령 접수(202) 뒤 SSE command.result 로 단계가 옴 → 여기서는 타이머로 흉내
 * - 경보 종료: 모든 센서가 경보 기준 아래일 때만 (셧다운 뒤)
 * - 화재: 16:58:04 감지 → 경고 방송 → 상도 건조로 자동 셧다운 → 본사 통보. 해제는 비밀번호 다시 입력(5회 틀리면 잠김)
 * - 위 시각은 설계안 시각표. 상황을 바꾸는 순간이 경보 발생(16:52:08) · 화재 감지(16:58:04)가 되도록 전체를 옮김 (scenario.js anchorAt)
 */
import { emitLive } from '../live'
import { DEFAULT_ITEMS, NO_EMERGENCY, anchorAt, replace, scenario, scenarioNow } from './scenario'
import { LEVEL } from '@/types/codes'

/** 경보 직전까지의 주의(1042): 확인 → 전송 → 5개 모두 완료 (설계안 raise('dng') 의 S.ck) */
function warnDone() {
  return {
    alarm: { id: 1042, status: 'ACTING', raisedAt: '16:38:20', ackedAt: '16:38:31', ackedBy: '한지우', closedAt: null },
    checklist: {
      id: 77,
      status: 'REPORTED',
      items: [...DEFAULT_ITEMS],
      assigneeId: 21,
      sentAt: '16:41:05',
      sentBy: '한지우',
      doneTimes: ['16:42:10', '16:44:35', '16:46:48', '16:48:02', '16:49:20'],
      memo: { at: '16:44:35', text: '구동 벨트 장력 느슨함 확인, 재조정함. 베어링 발열은 없음.' },
      remindCount: 0,
      lastRemindMs: 0,
      reassigned: [],
      reportedAt: '16:49:20',
      confirmedAt: null,
      result: null,
    },
  }
}

/** 경보 발생 (설계안 16:52:08 → 지금) */
export function raiseAlarm() {
  replace(
    anchorAt('16:52:08', {
      state: LEVEL.ALARM,
      ...warnDone(),
      ...NO_EMERGENCY(),
      incident: { id: 1051, status: 'OPEN', raisedAt: '16:52:08', closedAt: null, result: null },
      clock: { base: '16:52:08', at: Date.now() },
    }),
    'alarm.created',
  )
}

/** 화재 발생 (설계안 16:58:04 → 지금) — 경보 중 상황 위에, 상도 건조로는 자동 셧다운 */
export function raiseFire() {
  replace(
    anchorAt('16:58:04', {
      state: LEVEL.FIRE,
      ...warnDone(),
      incident: { id: 1051, status: 'OPEN', raisedAt: '16:52:08', closedAt: null, result: null },
      fire: { id: 3, status: 'ACTIVE', detectedAt: '16:58:04', failCount: 0, locked: false, clearedAt: null, clearedBy: null },
      shutdown: { equipmentIds: [31], startedAt: '16:58:06', steps: ['16:58:06', '16:58:06', '16:58:06'], auto: true },
      shut: true,
      clock: { base: '16:58:04', at: Date.now() },
    }),
    'fire.started',
  )
}

/* ───────── 셧다운 ───────── */

const STEP_MS = [1300, 1300, 2400]

/**
 * 셧다운 시작 (설비 몇 대 또는 공정 전체)
 * @returns {boolean} 시작했는지 (이미 진행 중이면 false)
 */
export function startShutdown(equipmentIds) {
  if (scenario.shutdown && scenario.shutdown.steps.some((s) => !s)) return false
  const run = { equipmentIds: [...equipmentIds], startedAt: scenarioNow(), steps: [null, null, null], auto: false }
  scenario.shutdown = run
  emitLive('command.result')
  // 단계마다 시각을 채움 (상황이 바뀌어 다른 셧다운이 되면 멈춤)
  let wait = 0
  STEP_MS.forEach((ms, i) => {
    wait += ms
    setTimeout(() => {
      if (scenario.shutdown?.startedAt !== run.startedAt) return
      scenario.shutdown.steps[i] = scenarioNow()
      if (i === 2) scenario.shut = true
      emitLive('command.result')
    }, wait)
  })
  return true
}

/** 지금 경보 기준을 넘은 센서가 있는지 (경보 종료 가능 여부) — 셧다운이 끝나야 내려감 */
export const valueStillOver = () => !(scenario.shutdown && scenario.shutdown.steps.every(Boolean))

/** 경보 종료 → 정상(정지 상태로), 오늘 알림은 모두 '해제' */
export function closeIncident({ result }) {
  const now = scenarioNow()
  scenario.incident = { ...scenario.incident, status: 'CLOSED', closedAt: now, result }
  if (scenario.alarm) scenario.alarm = { ...scenario.alarm, status: 'CLOSED', closedAt: now }
  if (scenario.checklist) scenario.checklist = { ...scenario.checklist, status: 'CONFIRMED', confirmedAt: now }
  scenario.state = LEVEL.NORMAL
  scenario.shut = true
  emitLive('alarm.updated')
}

/* ───────── 화재 해제 ───────── */

export const FIRE_MAX_FAIL = 5

/**
 * 비밀번호 다시 입력해서 화재 해제
 * @returns {{ ok: boolean, remaining?: number, locked?: boolean }}
 */
export function clearFire({ passwordOk, by }) {
  const f = scenario.fire
  if (f.locked) return { ok: false, locked: true }
  if (!passwordOk) {
    f.failCount += 1
    f.locked = f.failCount >= FIRE_MAX_FAIL
    emitLive('fire.updated')
    return { ok: false, remaining: FIRE_MAX_FAIL - f.failCount, locked: f.locked }
  }
  const now = scenarioNow()
  scenario.fire = { ...f, status: 'CLEARED', clearedAt: now, clearedBy: by }
  scenario.incident = { ...scenario.incident, status: 'CLOSED', closedAt: now }
  if (scenario.alarm) scenario.alarm = { ...scenario.alarm, status: 'CLOSED', closedAt: now }
  if (scenario.checklist) scenario.checklist = { ...scenario.checklist, status: 'CONFIRMED', confirmedAt: now }
  scenario.state = LEVEL.NORMAL
  scenario.shut = true
  emitLive('fire.cleared')
  return { ok: true }
}

/** 본사 승인으로 해제 잠금 풀기 */
export function approveUnlock() {
  if (!scenario.fire) return
  scenario.fire = { ...scenario.fire, failCount: 0, locked: false }
  emitLive('fire.updated')
}
