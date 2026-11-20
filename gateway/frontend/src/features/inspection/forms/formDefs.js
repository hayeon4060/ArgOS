/**
 * 서류 양식 F02 환기·배기설비 점검일지의 칸 정의 (팀 양식 PDF ArgOS_서류양식.pdf 의 F02 쪽 그대로)
 * - 점검 관리 화면은 F02 한 가지만 씀 (2026-11-20 재원님: 변전소(F03 수변전설비) 점검은 점검 관리에서 뺌)
 *
 * - 양식 값(values)의 키는 GET /doc-forms 의 fields.key 와 같음
 *   → 수기 서류(OCR 인식 결과)와 전자 작성이 같은 양식 · 같은 키로 저장됨
 *   예: inspectedAt, inspType, factory, equipmentNo, inspector, checker,
 *       item01 (양호 · 불량 · 해당 없음), item02Value (측정값), item01Note (비고),
 *       issue1No · issue1Problem · issue1Action · issue1Due · issue1DoneAt, verdict, opinion
 * - 용어는 양식 그대로: 양호 / 불량, 종합 판정 정상 운전 / 조건부 운전 / 운전 중지
 */

const pad = (n) => String(n).padStart(2, '0')

/** 점검 항목 n 번째 결과 키 (item01 …) */
export const itemKey = (n) => `item${pad(n)}`

/** 점검 항목 결과 3칸 */
export const RESULT_OPTIONS = ['양호', '불량', '해당 없음']

/** 공장 */
export const FACTORY_OPTIONS = ['울산', '아산', '전주'].map((v) => ({ value: v, label: v }))

/** [점검 항목, 점검 기준, 측정값 단위(없으면 회색 칸)] */
const F02_ITEMS = [
  ['배기팬 가동 상태', '정상 회전, 이상 소음·진동 없음'],
  ['모터 운전 전류', '정격 전류 이하', 'A'],
  ['베어링 온도', '이상 발열 없음', '℃'],
  ['팬 벨트', '장력 적정, 마모·균열 없음'],
  ['덕트·후드', '파손·누설·이물질 없음'],
  ['댐퍼', '개폐 상태 정상, 위치 표시 일치'],
  ['필터 차압', '오염·막힘 없음, 관리 기준 이내', 'Pa'],
  ['배기 풍량', '설계 풍량 이상', 'm³/min'],
  ['제어반', '운전·고장 표시등 정상, 인터록 작동'],
  ['CO 감지기', '영점 정상, 경보 작동 시험 통과', 'ppm'],
  ['비상 정지 장치', '정상 작동'],
  ['예비 팬 전환', '주 팬 정지 시 자동 전환 정상'],
]

const opts = (list) => list.map((v) => (Array.isArray(v) ? { value: v[0], label: v[1] } : { value: v, label: v }))

/** 양식 머리 · 고르는 칸 · 줄 수 (키는 양식 번호) */
export const FORM_DEFS = {
  F02: {
    code: 'ARG-F02',
    title: '환기·배기설비 점검일지',
    sub: '배기팬 · 덕트 · 가스 감지기',
    eqPrefix: 'EF',
    items: F02_ITEMS,
    issueRows: 3,
    inspTypes: opts(['일상', '주간', '월간']),
    zones: opts(['시험 챔버', '도장', '차체 용접', '변전소', '기타']),
    verdicts: opts(['정상 운전', ['조건부 운전', '조건부 운전 (조치 후 재점검)'], '운전 중지']),
  },
}

/**
 * 새로 쓰는 양식의 처음 값 (점검 건의 날짜 · 시각 · 설비 · 점검자를 미리 채움)
 * @param {{ date, time, equipmentNo, inspector }} base
 */
export function blankValues({ date, time, equipmentNo, inspector } = {}) {
  const v = {
    inspectedAt: date && time ? `${date}T${time}` : '',
    inspType: '일상',
    factory: '울산',
    equipmentNo: equipmentNo ? equipmentNo.replace(/^[A-Z]+-/, '') : '', // 'EF-03' → '03' (양식에 'EF - ' 가 인쇄돼 있음)
    inspector: inspector ?? '',
    checker: '',
    zone: '도장',
    verdict: '',
    opinion: '',
  }
  return v
}

/** 결과: 점검 항목에 '불량'이 하나라도 있으면 NG */
export function resultOf(formType, values) {
  const n = FORM_DEFS[formType].items.length
  for (let i = 1; i <= n; i++) if (values[itemKey(i)] === '불량') return 'NG'
  return 'OK'
}

/** 다 적었는지: 점검 항목마다 결과 하나씩 + 종합 판정 → 빠진 것 안내 문구 (다 적었으면 '') */
export function missingOf(formType, values) {
  const n = FORM_DEFS[formType].items.length
  const empty = []
  for (let i = 1; i <= n; i++) if (!values[itemKey(i)]) empty.push(i)
  if (empty.length) return `점검 항목 ${empty.join(', ')}번의 결과(양호 · 불량 · 해당 없음)를 골라 주세요.`
  if (!values.verdict) return '종합 판정을 골라 주세요.'
  if (!values.inspector) return '점검자를 적어 주세요.'
  return ''
}

/** '2026-11-27' → '26 . 11 . 27' (양식의 '__ . __ . __' 칸) */
export const shortDate = (s) => (s ? s.slice(2).replace(/-/g, ' . ') : '')

/** '2026-11-20T09:10' → '2026 . 11 . 20   09 : 10' (양식의 점검 일자 칸) */
export const longDateTime = (s) => (s ? `${s.slice(0, 4)} . ${s.slice(5, 7)} . ${s.slice(8, 10)}   ${s.slice(11, 13)} : ${s.slice(14, 16)}` : '')
