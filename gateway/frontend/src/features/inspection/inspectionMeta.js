/**
 * 점검 관리 공통 이름표 (화면설계안 vInspect() 의 CHIP · 단계 · 구역)
 */

/** 서류 종류 */
export const FORM_TYPES = [
  { value: 'F01', label: '밀폐공간 작업허가서' },
  { value: 'F02', label: '환기·배기설비 점검일지' },
  { value: 'F03', label: '수변전설비 점검일지' },
  { value: 'F04', label: '안전보건교육 일지' },
  { value: 'F05', label: '위험성평가표' },
]
export const FORM_LABEL = Object.fromEntries(FORM_TYPES.map((f) => [f.value, f.label]))

/** 예시 원본이 있는 서류 (설계안: 밀폐공간 작업허가서만) */
export const SAMPLE_FORM = 'F01'

/** 등록 목록 상태 칩 (current: 지금 열어 둔 서류면 '검수 중') */
export function statusChip(status, current = false) {
  if (status === 'REVIEW') return current ? { cls: 'info', label: '검수 중' } : { cls: 'warn', label: '검수 대기' }
  return (
    {
      UPLOADING: { cls: 'gray', label: '올리는 중' },
      RECOGNIZING: { cls: 'info', label: '인식 중' },
      DONE: { cls: 'ok', label: '저장' },
      FAILED: { cls: 'alarm', label: '인식 실패' },
    }[status] ?? { cls: 'gray', label: status }
  )
}

/** 단계 4개 */
export const STEPS = ['파일 올리기', '문자 인식', '원본 대조', '저장']

/**
 * 인식 결과 구역 (doc-forms 의 group) — 이 순서대로 그림
 * - F01: overview · gas · pre · end
 * - F02 ~ F05: info · measure · items · issues · verdict · edu · content · people · roster · risk
 */
export const GROUPS = {
  overview: { label: '작업 개요', icon: 'file' },
  gas: { label: '가스 농도 측정', icon: 'wind' },
  pre: { label: '사전 안전조치', icon: 'shield' },
  end: { label: '작업 종료 확인', icon: 'user' },
  info: { label: '점검 정보', icon: 'file' },
  edu: { label: '교육 정보', icon: 'file' },
  measure: { label: '계측 기록', icon: 'zap' },
  items: { label: '점검 항목', icon: 'clip' },
  issues: { label: '이상 사항 및 조치', icon: 'alert' },
  verdict: { label: '종합 판정', icon: 'check' },
  content: { label: '교육 내용', icon: 'clip' },
  people: { label: '교육 인원', icon: 'users' },
  roster: { label: '수강자 명단', icon: 'user' },
  risk: { label: '위험성 평가 및 감소 대책', icon: 'shield' },
}

/**
 * 점검 상태 (스케줄 한 건) — 이름은 여기 한 곳에서만 바꿈
 * - 코드는 백엔드 InspectionService.scheduleStatus 그대로: DONE · PLANNED · DELAYED
 * - DELAYED 는 화면에서 '누락'으로 씀 (2026-11-20 개편안 수정안 1. '지연'과 통일할지 재원님 확인 중 → 바꾸려면 label 만 고침)
 * - cls: 달력 칩 · 이력 배지 색 (done 초록 / plan 파랑 / miss 빨강)
 */
export const SCHEDULE_STATE = {
  DONE: { label: '완료', cls: 'done' },
  PLANNED: { label: '예정', cls: 'plan' },
  DELAYED: { label: '누락', cls: 'miss' },
}
/** 달력 · 합계에 그리는 순서 */
export const STATE_ORDER = ['DONE', 'PLANNED', 'DELAYED']

/** 점검 구분 (완료 건만): 수기 = 종이에 써서 스캔해 올림 / 전자 = 화면의 서류 양식에 바로 작성 */
export const SOURCE = {
  PAPER: { label: '수기', cls: 'paper' },
  DIGITAL: { label: '전자', cls: 'digital' },
}

/** 점검 결과 (완료 건만): 서류 양식의 점검 항목에 '불량'이 하나라도 있으면 NG */
export const RESULT = {
  OK: { label: '양호', cls: 'done' },
  NG: { label: '불량', cls: 'miss' },
}

/** 점검 이력 기간: 오늘 기준 최근 N일 (수정안 4. 기본 7일) */
export const PERIODS = [
  { value: 1, label: '1일' },
  { value: 7, label: '7일' },
  { value: 30, label: '30일' },
]

/**
 * 점검 관리 화면에서 쓰는 서류 양식 = F02 환기·배기설비 점검일지 하나 (팀 양식 PDF 의 F02 쪽)
 * - 2026-11-20 재원님: 변전소(F03 수변전설비) 점검은 점검 관리에서 뺌 → 양식 고르는 칸도 없음
 * - FORM_TYPES(위)는 수기 서류 등록(OCR)이 쓰는 공통 목록이라 F01 ~ F05 그대로 둠
 */
export const PAPER_FORM = 'F02'

/** 스케줄 반복 */
export const REPEATS = [
  { value: 'ONCE', label: '한 번' },
  { value: 'DAILY', label: '매일' },
  { value: 'WEEKLY', label: '매주' },
  { value: 'MONTHLY', label: '매월' },
]

/** 'YYYY-MM' 을 n달 옮기기 */
export function shiftMonth(ym, n) {
  const d = new Date(Number(ym.slice(0, 4)), Number(ym.slice(5, 7)) - 1 + n, 1)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}
/** '2026-11' → '2026년 11월' */
export const monthName = (ym) => `${ym.slice(0, 4)}년 ${Number(ym.slice(5, 7))}월`
/** 'YYYY-MM-DD' → Date (그날 0시) */
export const toDate = (s) => new Date(Number(s.slice(0, 4)), Number(s.slice(5, 7)) - 1, Number(s.slice(8, 10)))
/** 'YYYY-MM-DD' 에서 n일 앞뒤 */
export const addDays = (s, n) => {
  const d = toDate(s)
  d.setDate(d.getDate() + n)
  return ymd(d)
}
/** 상태별 개수 { DONE, PLANNED, DELAYED } */
export function countStates(list) {
  const c = { DONE: 0, PLANNED: 0, DELAYED: 0 }
  list.forEach((it) => (c[it.status] += 1))
  return c
}

const pad = (n) => String(n).padStart(2, '0')
/** Date → 'YYYY-MM-DD' (PC 시간 기준) */
export const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
/** '2026-11-12T16:44:02+09:00' → '11-12 16:44' */
export const shortTime = (iso) => (iso ? `${iso.slice(5, 10)} ${iso.slice(11, 16)}` : '')
