/**
 * 이용 로그 화면 공통 값 (화면설계안 v-rev3.js LOGT · vLogs(), 명세서 G2 type)
 *
 * 유형: 설계안의 8칸 ↔ 명세서·백엔드 type 코드
 * - 로그인     LOGIN · LOGOUT
 * - 조치       CHECKLIST(주의 확인 · 체크리스트 전송·확인·담당자 변경) · FAN(배기팬 조작) — 백엔드 ActivityLogger 코드
 * - 셧다운     SHUTDOWN · FIRE_CLEAR (설계안 '경보 해제 · 비밀번호 확인'이 셧다운 칸)
 * - 기준값 변경 THRESHOLD
 * - 설비 수정   LAYOUT · FACTORY (공장 관리)
 * - 사용자     USER
 * - 서류 등록   INSPECTION
 * - 내려받기   EXPORT
 */

/** 유형 칸 { key, label, chip(칩 색 — alert.css .m-chip, sky 는 표에서), color(막대 색, 설계안), types } */
export const LOG_GROUPS = [
  { key: 'login', label: '로그인', chip: 'gray', color: '#94A3B8', types: ['LOGIN', 'LOGOUT'] },
  { key: 'act', label: '조치', chip: 'info', color: '#2563EB', types: ['CHECKLIST', 'FAN'] },
  { key: 'shut', label: '셧다운', chip: 'alarm', color: '#DC2626', types: ['SHUTDOWN', 'FIRE_CLEAR'] },
  { key: 'th', label: '기준값 변경', chip: 'warn', color: '#F97316', types: ['THRESHOLD'] },
  { key: 'eq', label: '설비 수정', chip: 'sky', color: '#0EA5E9', types: ['LAYOUT', 'FACTORY'] }, // FACTORY = 사업장 · 공장 · 공정 추가, 배치도 올리기 (백엔드 FactoryAdminService) — 2차 검증에서 추가
  { key: 'user', label: '사용자', chip: 'ok', color: '#16A34A', types: ['USER'] },
  { key: 'doc', label: '서류 등록', chip: 'gray', color: '#94A3B8', types: ['INSPECTION'] },
  { key: 'rep', label: '내려받기', chip: 'gray', color: '#94A3B8', types: ['EXPORT'] },
]

/** type 코드 → 유형 칸 (모르는 코드는 null) */
export const groupOfType = (type) => LOG_GROUPS.find((g) => g.types.includes(type)) ?? null

/** 기간 (설계안: 오늘 / 7일 / 30일, 처음 7일) */
export const PERIODS = [
  { value: 'today', label: '오늘', days: 1, sub: '오늘' },
  { value: '7d', label: '7일', days: 7, sub: '최근 7일' },
  { value: '30d', label: '30일', days: 30, sub: '최근 30일' },
]

const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** 기간 → { from, to } ('YYYY-MM-DD', 오늘 포함) */
export function rangeOf(period, now = new Date()) {
  const days = PERIODS.find((p) => p.value === period)?.days ?? 7
  const from = new Date(now)
  from.setDate(from.getDate() - (days - 1))
  return { from: ymd(from), to: ymd(now) }
}

/** 시간대 막대 색: 낮(7~18시) 파랑, 그 밖 회색 (설계안) */
export const hourColor = (h) => (h >= 7 && h < 19 ? '#3B82F6' : '#94A3B8')
