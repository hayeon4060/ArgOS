/**
 * 통계·이력 관리 공통 도우미 (화면설계안 vStats() 의 F · RANGE · PLABEL)
 * - 조건 줄 값 → API 조건(criteria) 만들기
 * - 기간 빠른 선택(오늘 / 7일 / 30일 / 직접 선택)의 날짜 범위
 * - 센서 종류·등급·근무조 이름, 알림 한 줄의 '조치' 문구
 */
const pad = (n) => String(n).padStart(2, '0')

/** Date → 'YYYY-MM-DD' (PC 시간 기준. toISOString 은 오전 9시 전에 어제 날짜가 되므로 쓰지 않음) */
export const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

/** 기간 빠른 선택 */
export const PERIODS = [
  { value: 'today', label: '오늘' },
  { value: '7d', label: '7일' },
  { value: '30d', label: '30일' },
  { value: 'custom', label: '직접 선택' },
]
/** 카드 설명에 쓰는 기간 이름 */
export const PERIOD_LABEL = { today: '오늘', '7d': '최근 7일', '30d': '최근 30일', custom: '선택한 기간' }

/** 센서 종류 (백엔드 sensorType 값) */
export const SENSOR_TYPES = [
  { value: 'CURRENT', label: '전류', icon: 'zap' },
  { value: 'TEMP', label: '온도', icon: 'temp' },
  { value: 'CO', label: 'CO', icon: 'wind' },
]
export const SENSOR_LABEL = { CURRENT: '전류', TEMP: '온도', CO: 'CO', IR: '열화상' }
export const SENSOR_ICON = { CURRENT: 'zap', TEMP: 'temp', CO: 'wind', IR: 'cam' }

/**
 * 유형 (백엔드 alarm.hazard 값): 화재 / 가스
 * - 서버가 hazard 를 안 주면 센서 종류로 정함: CO → 가스, 전류·온도·열화상 → 화재
 */
export const HAZARDS = [
  { value: '', label: '유형 전체' },
  { value: 'FIRE', label: '화재' },
  { value: 'GAS', label: '가스' },
]
/*
 * 유형 칩의 글자·아이콘·아이콘 색 (2026-11-20 재원님 요청으로 바꿈)
 * - 화재: 불꽃, 빨강 (전에는 보라)
 * - 가스: 가스 구름, 연두 (전에는 바람 모양 · 청록). 정상(초록 #16a34a)보다 노란 쪽이라 구분됨
 * 색을 바꾸려면 여기 color 만 고치면 됨 (알림 이력 표 · 상세 패널이 같이 씀)
 */
export const HAZARD_META = {
  FIRE: { label: '화재', icon: 'flame', color: '#dc2626' },
  GAS: { label: '가스', icon: 'gas', color: '#65a30d' },
}
export const hazardOf = (row) => row.hazard ?? (row.sensorType === 'CO' ? 'GAS' : 'FIRE')

/** 등급 (백엔드 level 값) */
export const LEVELS = [
  { value: '', label: '전체' },
  { value: 'WARN', label: '주의' },
  { value: 'ALARM', label: '경보' },
]

/** 근무조 (백엔드 shift 값, 주간 07~19시) */
export const SHIFTS = [
  { value: '', label: '통합' },
  { value: 'DAY', label: '주간 (07–19시)' },
  { value: 'NIGHT', label: '야간 (19–07시)' },
]

/** 오늘 0시 기준 n일 더한 'YYYY-MM-DD' */
export function dayFrom(today, n) {
  return ymd(new Date(today.getFullYear(), today.getMonth(), today.getDate() + n))
}

/** 기간 빠른 선택 → { from, to } (오늘 포함) */
export function rangeOf(period, now = new Date()) {
  const to = ymd(now)
  if (period === 'today') return { from: to, to }
  if (period === '7d') return { from: dayFrom(now, -6), to }
  return { from: dayFrom(now, -29), to }
}

/** 한 번에 조회할 수 있는 최대 일수 (양 끝 포함, 백엔드 RANGE_TOO_LARGE 와 같은 값 — 2026-11-19 1년 → 30일) */
export const MAX_RANGE_DAYS = 30

/** 두 날짜 사이 일수 (양 끝 포함) */
export function daysBetween(from, to) {
  return Math.round((new Date(`${to}T00:00:00`) - new Date(`${from}T00:00:00`)) / 86400000) + 1
}

/**
 * 조건 줄 값 → API 조건
 * @param {object} f 조건 줄 { from, to, factoryId, processId, sensorType, level, shift }
 * @param {number|string} siteId
 */
export function criteriaOf(f, siteId) {
  return {
    from: f.from,
    to: f.to,
    siteId: Number(siteId),
    factoryId: f.factoryId || '',
    processId: f.processId || '',
    sensorType: f.sensorType || '',
    level: f.level || '',
    shift: f.shift || '',
  }
}

/** 지금 조치 중인 상태 (백엔드 status) */
export const isActive = (row) => ['OPEN', 'ACKED', 'ACTING'].includes(row.status)

/**
 * 알림 한 줄의 '조치' 칸 (설계안: 체크리스트 n/n · 설비 셧다운 · 확인 대기)
 * - 응답에 '어떤 조치였는지' 필드가 없어 체크리스트·등급·상태로 정함 (backend-notes 12장)
 */
export function actionText(row) {
  if (row.checklist) return `체크리스트 ${row.checklist.doneCount}/${row.checklist.totalCount}`
  if (row.level !== 'WARN' && row.closedAt) return '설비 셧다운'
  if (row.status === 'OPEN') return '확인 대기'
  if (row.status === 'ACKED') return '확인함'
  return '조치 중'
}

/** 조치 시간(분): 끝난 건은 actionMinutes, 조치 중이면 지금까지 지난 분 */
export function minutesOf(row, now = Date.now()) {
  if (row.actionMinutes != null) return row.actionMinutes
  return Math.max(1, Math.round((now - new Date(row.raisedAt).getTime()) / 60000))
}

/** '2026-11-12T16:38:20+09:00' → '11-12 16:38' */
export const shortTime = (iso) => `${iso.slice(5, 10)} ${iso.slice(11, 16)}`

/** location '5공장 · 도장 공정 · 상도 건조로 배기팬' → { factory, process, equipment } */
export function placeOf(location = '') {
  const [factory = '', ...rest] = location.split(' · ')
  // '공장 · 설비' 처럼 공정이 빠진 위치도 있음 → 마지막 칸이 설비
  if (rest.length < 2) return { factory, process: '', equipment: rest[0] ?? factory }
  return { factory, process: rest[0], equipment: rest.slice(1).join(' · ') }
}
