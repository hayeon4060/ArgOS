/**
 * 설비 이벤트 종류별 표시 (설계안 vEquip() evType / TY)
 * - 점 색: 알람은 단계 색(정상이면 회색), 알림에 딸린 조치는 주황, 기록은 초록, 기준값 조정은 짙은 회색
 * - 배지: 알람(주의 주황 · 경보 빨강), 조치(파랑, 진행 중이면 '조치 중'), 기준값(회색), 기록(초록)
 * - 걸러 보기: 전체 / 알람 / 조치 / 기준값 (기록은 '전체'에서만)
 */
import { LEVEL } from '@/types/codes'

export const EVENT_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'ALARM', label: '알람' },
  { value: 'ACTION', label: '조치' },
  { value: 'THRESHOLD', label: '기준값' },
]

const DOT = { warn: '#f97316', alarm: '#dc2626', gray: '#94a3b8', ok: '#16a34a', slate: '#64748b' }

/** 점 색 */
export function dotColor(e) {
  if (e.type === 'ALARM') {
    if (e.level === LEVEL.WARN) return DOT.warn
    if (e.level === LEVEL.ALARM || e.level === LEVEL.FIRE) return DOT.alarm
    return DOT.gray
  }
  if (e.type === 'ACTION') return e.alarmId ? DOT.warn : DOT.ok
  if (e.type === 'THRESHOLD') return DOT.slate
  return DOT.ok
}

/** 배지 { cls, label } */
export function chipOf(e) {
  if (e.type === 'ALARM') {
    const cls = e.level === LEVEL.WARN ? 'warn' : e.level === LEVEL.ALARM || e.level === LEVEL.FIRE ? 'alarm' : 'gray'
    return { cls, label: '알람' }
  }
  if (e.type === 'ACTION') return { cls: 'pri', label: e.status === 'ACTING' ? '조치 중' : '조치' }
  if (e.type === 'THRESHOLD') return { cls: 'gray', label: '기준값' }
  return { cls: 'ok', label: '기록' }
}

/** 'HH:mm:ss' */
export const timeOf = (iso) => new Date(iso).toTimeString().slice(0, 8)
