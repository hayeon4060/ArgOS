/**
 * 월별 점검 보고서 계산 (프론트에서 계산 — 별도 보고서 API 없이 GET /inspections/schedule?month= 목록으로)
 *
 * - 기간: 그 달 1일 ~ 말일. 이번 달이면 1일 ~ 오늘까지만
 * - 요약: 점검 대상 · 완료 · 예정 · 누락 · 불량
 * - 서류 양식별 · 설비별 표: 대상 · 완료 · 누락 · 불량 · 이행 막대(완료 초록 · 누락 빨강)
 * - 불량 목록 · 누락 목록
 */
import { FORM_LABEL, countStates } from './inspectionMeta'

/** 'YYYY-MM' 의 말일 'YYYY-MM-DD' */
const lastDay = (ym) => {
  const d = new Date(Number(ym.slice(0, 4)), Number(ym.slice(5, 7)), 0)
  return `${ym}-${String(d.getDate()).padStart(2, '0')}`
}

/** 한 묶음의 숫자 */
function sumUp(list) {
  const c = countStates(list)
  return { total: list.length, done: c.DONE, planned: c.PLANNED, missed: c.DELAYED, ng: list.filter((it) => it.result === 'NG').length }
}

/** list 를 key 별로 묶어 [{ key, label, ...sumUp }] (처음 나온 순서) */
function groupBy(list, keyOf, labelOf) {
  const map = new Map()
  list.forEach((it) => {
    const k = keyOf(it)
    if (!map.has(k)) map.set(k, { key: k, label: labelOf(it), list: [] })
    map.get(k).list.push(it)
  })
  return [...map.values()].map((g) => ({ key: g.key, label: g.label, ...sumUp(g.list) }))
}

/**
 * @param {Array} items 그 달 점검 건 (getSchedule({ month }))
 * @param {string} month 'YYYY-MM'
 * @param {string} today 'YYYY-MM-DD'
 */
export function buildReport(items, month, today) {
  const from = `${month}-01`
  const end = lastDay(month)
  const to = month === today.slice(0, 7) ? today : end
  const future = from > today
  const list = future ? [] : items.filter((it) => it.date >= from && it.date <= to)
  const byDate = (a, b) => (a.date + a.time).localeCompare(b.date + b.time)
  return {
    from,
    to,
    partial: to !== end, // 이번 달이라 오늘까지만
    future, // 아직 시작하지 않은 달
    sum: sumUp(list),
    byForm: groupBy(
      [...list].sort((a, b) => a.formType.localeCompare(b.formType)),
      (it) => it.formType,
      (it) => `${it.formType} ${FORM_LABEL[it.formType].replace(' 점검일지', '')}`, // 표 칸이 좁아 'F02 환기·배기설비' 까지만
    ),
    byEquipment: groupBy(
      [...list].sort((a, b) => a.equipmentId - b.equipmentId),
      (it) => it.equipmentId,
      (it) => it.equipmentName,
    ),
    ngList: list.filter((it) => it.result === 'NG').sort(byDate),
    missedList: list.filter((it) => it.status === 'DELAYED').sort(byDate),
  }
}
