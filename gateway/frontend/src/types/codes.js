/**
 * 공통 코드 (상태 단계, 권한)
 * - 백엔드와 같은 문자열 값을 사용해야 하므로 화면 코드에서 문자열을 직접 쓰지 말고 이 상수를 사용
 * - 값이 바뀌면 이 파일만 고치면 됨
 */

/** 상태 4단계 */
export const LEVEL = Object.freeze({
  NORMAL: 'NORMAL', // 정상
  WARN: 'WARN', // 주의
  ALARM: 'ALARM', // 경보
  FIRE: 'FIRE', // 화재
})

/**
 * 상태별 화면 표시 정보
 * - label: 이름, key: CSS 클래스용 이름(is-normal 등), icon: AppIcon 이름
 * - color: 진한 색(점·선·도넛) — tokens.css 의 변수
 */
export const LEVEL_META = Object.freeze({
  [LEVEL.NORMAL]: { label: '정상', key: 'normal', icon: 'check', color: 'var(--color-level-normal)' },
  [LEVEL.WARN]: { label: '주의', key: 'warn', icon: 'tri', color: 'var(--color-level-warn)' },
  [LEVEL.ALARM]: { label: '경보', key: 'alarm', icon: 'alert', color: 'var(--color-level-alarm)' },
  [LEVEL.FIRE]: { label: '화재', key: 'fire', icon: 'flame', color: 'var(--color-level-fire)' },
})

/** 센서 종류 (API 의 sensorType / levelsByType 키) — 표시 이름·아이콘(AppIcon)·단위·소수 자릿수(dec) */
export const SENSOR_TYPES = Object.freeze([
  { type: 'CURRENT', label: '전류', icon: 'zap', unit: 'A', dec: 1 },
  { type: 'TEMP', label: '온도', icon: 'temp', unit: '℃', dec: 0 },
  { type: 'CO', label: 'CO', icon: 'wind', unit: 'ppm', dec: 0 },
])

/** 센서 종류 정보 찾기 ('CURRENT' → { label: '전류', ... }) */
export const sensorTypeOf = (type) => SENSOR_TYPES.find((t) => t.type === type)

/** 알림 진행 상태 (alarm.status) — 화면 표시 이름 */
export const ALARM_STATUS_LABEL = Object.freeze({
  OPEN: '발생 중',
  ACKED: '확인함',
  ACTING: '조치 중',
  CLOSED: '조치 완료',
})

/** 센서 단계 3가지 (정상·주의·경보) 표시 순서 — 개수 표시·막대·도넛에서 공통 사용 */
export const SENSOR_STAGES = Object.freeze([LEVEL.NORMAL, LEVEL.WARN, LEVEL.ALARM])

/** 권한 3종 */
export const ROLE = Object.freeze({
  HQ_ADMIN: 'HQ_ADMIN', // 본사 관리자: 전체 사업장, PC 웹
  PLANT_ADMIN: 'PLANT_ADMIN', // 공장 담당자: 담당 공장, PC 웹
  WORKER: 'WORKER', // 공정 담당자: 배정 공정, 모바일 앱
})

/** 권한별 화면 표시 이름 */
export const ROLE_LABEL = Object.freeze({
  [ROLE.HQ_ADMIN]: '본사 관리자',
  [ROLE.PLANT_ADMIN]: '공장 담당자',
  [ROLE.WORKER]: '공정 담당자',
})

/** PC 웹에 들어올 수 있는 권한 (WORKER 는 모바일 앱 사용) */
export const WEB_ROLES = Object.freeze([ROLE.HQ_ADMIN, ROLE.PLANT_ADMIN])

/**
 * 가스(CO) 알림 때 배기팬 자동 제어 (2026-11-19 재원님 결정, 값은 최종 ERD equipment_command 기준)
 * - 설비 배기팬이 '자동'일 때만: 주의 75%, 경보 100%
 * - %는 배기팬 최고 회전수(1,800 rpm) 기준 → 75% = 1,350 rpm, 100% = 1,800 rpm
 * - DB 는 주파수(Hz)로 저장: 평소 40 Hz / 가스 주의 45 Hz / 경보 60 Hz, 회전수 = 30 × Hz (4극 모터)
 *   → 평소 1,200 rpm / 주의 1,350 rpm / 경보 1,800 rpm. 화면은 rpm 으로만 보여 줌
 * - 알림이 끝나면 서버가 평소 회전수(40 Hz)로 되돌림 (equipment_command.source = RECOVERY)
 * - 실제로 팬을 돌리는 건 서버(알림이 생길 때). 화면은 알림 응답의 fanAuto 를 보여 주기만 함
 */
export const FAN_AUTO_PERCENT = Object.freeze({ WARN: 75, ALARM: 100 })
export const FAN_MAX_RPM = 1800
/** 배기팬 평소 회전수 (40 Hz × 30) — 알림이 끝나면 이 값으로 돌아옴 */
export const FAN_NORMAL_RPM = 1200
