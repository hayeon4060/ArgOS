/**
 * 알림 한 줄을 화면에 보일 때 쓰는 값 (GET /alarms 의 items 하나 → 아이콘 · 색 · 진행 상태 칩)
 * - 오늘 알림 내역 카드(features/dashboard/AlarmRow.vue)와 상단 알림 종 목록(components/AppBellMenu.vue)이 같이 씀
 */
import { hazardDisplayOf } from '@/features/history/historyFilter'
import { ALARM_STATUS_LABEL, LEVEL_META, SENSOR_TYPES } from '@/types/codes'

/** 목록과 주의·경보 창에 사용하는 유형 문구 */
export const hazardLabelOf = (alarm) => hazardDisplayOf(alarm)?.label ?? ''

/** 조치가 끝나지 않은 알림인지 (왼쪽 색 띠 · 옅은 배경을 보일지) */
export const isActiveAlarm = (alarm) => alarm.status !== 'CLOSED'

/** 단계 이름표 (normal · warn · alarm · fire) — CSS 클래스 is-… 에 씀 */
export const levelKeyOf = (alarm) => LEVEL_META[alarm.level]?.key ?? 'normal'

/** 센서 종류 아이콘 (전류 · 온도 · CO, 모르면 경고 아이콘) */
export const alarmIconOf = (alarm) => SENSOR_TYPES.find((t) => t.type === alarm.sensorType)?.icon ?? 'alert'

/**
 * 진행 상태 칩 { cls, text }
 * - 발생 중: 알림 단계 색 / 확인함: 파랑 / 조치 중: '체크리스트 2/5' 파랑, 다 끝나면 '완료 · 확인 필요' 초록 / 조치 완료: 초록
 */
export function alarmChipOf(alarm) {
  const { status, checklist: c } = alarm
  if (status === 'OPEN') return { cls: `is-${levelKeyOf(alarm)}`, text: ALARM_STATUS_LABEL.OPEN }
  if (status === 'CLOSED') return { cls: 'is-done', text: ALARM_STATUS_LABEL.CLOSED }
  if (status === 'ACTING' && c) {
    if (c.status === 'REPORTED') return { cls: 'is-done', text: '완료 · 확인 필요' }
    return { cls: 'is-info', text: `체크리스트 ${c.doneCount}/${c.totalCount}` }
  }
  return { cls: 'is-info', text: ALARM_STATUS_LABEL[status] }
}

/** 발생 시각: '2026-11-16T16:38:20+09:00' → '16:38:20' */
export const alarmTimeOf = (alarm) => alarm.raisedAt?.slice(11, 19) ?? ''
