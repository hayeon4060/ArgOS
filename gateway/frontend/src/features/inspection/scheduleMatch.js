/**
 * 스캔본 ↔ 점검 건 맞추기 (수기 서류 등록 창의 '점검 건 연결')
 *
 * 2026-11-20 재원님 결정: 자동으로 맞춰 보여 주고, 사용자가 확인하거나 다른 건으로 바꿀 수 있게
 * - 1순위: 백엔드가 맞춰 준 건 (GET /inspections/{id}/ocr 의 scheduleId — 백엔드 추가 요청)
 * - 2순위: 화면에서 맞춤 — 양식이 같은 건 중에서 '설비가 같음(2점) + 날짜가 같음(1점)' 점수가 높은 건
 *   (설비 · 날짜 둘 다 다르면 추천하지 않음 → 사용자가 고르거나 '연결 안 함')
 * - 후보: 오늘 포함 최근 7일의 예정 · 누락 건 (완료 건은 다시 잇지 않음)
 */

/** 인식 값에서 날짜를 찾는 칸 (양식마다 이름이 다름: F02 inspectedAt, F01 applyDate …) */
const DATE_KEYS = ['inspectedAt', 'checkDate', 'applyDate', 'workDate']

const valueOf = (fields, key) => fields.find((f) => f.key === key)?.value ?? ''

/** '2026. 11. 20 09:10' · '2026-11-20T09:10' → '2026-11-20' (못 읽으면 '') */
export function readDate(fields) {
  for (const k of DATE_KEYS) {
    const m = String(valueOf(fields, k)).match(/(\d{4})\D{1,3}(\d{1,2})\D{1,3}(\d{1,2})/)
    if (m) return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`
  }
  return ''
}

/** 설비번호 숫자 부분: 'EF-03' · 'EF - 3' · '03' → '03' (못 읽으면 '') */
export function readEquipmentNo(fields) {
  const m = String(valueOf(fields, 'equipmentNo')).match(/(\d+)\s*$/)
  return m ? m[1].padStart(2, '0') : ''
}

/**
 * 추천할 점검 건 번호 (없으면 null)
 * @param {Array} candidates 예정 · 누락 건 (getSchedule 한 줄 모양)
 * @param {{ formType: string, fields: Array, scheduleId?: number|null }} doc 인식한 서류
 */
export function suggestSchedule(candidates, { formType, fields = [], scheduleId = null }) {
  if (scheduleId && candidates.some((c) => c.scheduleId === scheduleId)) return scheduleId
  const date = readDate(fields)
  const no = readEquipmentNo(fields)
  let best = null
  let bestScore = 0
  candidates
    .filter((c) => c.formType === formType)
    .forEach((c) => {
      const score = (no && c.equipmentNo?.endsWith(`-${no}`) ? 2 : 0) + (date && c.date === date ? 1 : 0)
      // 점수가 같으면 날짜가 늦은 건 (오늘 건 우선)
      if (score > bestScore || (score === bestScore && score > 0 && c.date > best.date)) {
        best = c
        bestScore = score
      }
    })
  return best?.scheduleId ?? null
}
