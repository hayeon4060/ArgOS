/**
 * 점검 건 목록 불러오기 (점검 스케줄 달력 · 점검 이력이 같이 씀)
 *
 * - 달력: 고른 달의 점검 건 (monthItems)
 * - 이력: 오늘 기준 최근 N일의 점검 건 (historyItems) — 구분 · 결과 · 찾기 거르기는 화면(HistoryCard)에서
 *   → 거르기를 바꿔도 다시 부르지 않고, '전체 N · 수기 N · 전자 N' 개수도 같은 목록에서 셈
 * - 둘 다 GET /inspections/schedule 하나에서 옴 → 달력 숫자와 이력 줄이 어긋나지 않음
 * - 오늘은 serverNow() (서버 시연 시계) 기준
 */
import { ref } from 'vue'
import { getSchedule } from '@/api/inspections'
import { serverNow } from '@/api/clock'
import { toast } from '@/utils/toast'
import { addDays, ymd } from './inspectionMeta'

export function useInspectionSchedule(factoryId) {
  const today = ref(ymd(serverNow()))
  const month = ref(today.value.slice(0, 7))
  const days = ref(7)
  const monthItems = ref([])
  const historyItems = ref([])
  const loading = ref(false)

  // 늦게 온 응답이 새 응답을 덮지 않게 (달을 빨리 넘길 때)
  let monthSeq = 0
  let historySeq = 0

  async function loadMonth() {
    const my = ++monthSeq
    try {
      const list = await getSchedule({ month: month.value, factoryId })
      if (my === monthSeq) monthItems.value = list
    } catch (error) {
      toast(error.message, { type: 'err' })
    }
  }

  /** 이력: 오늘 포함 최근 N일, 오늘 이후(앞날 예정)는 빼고 최신순 → 오늘 점검이 맨 위 */
  async function loadHistory() {
    const my = ++historySeq
    loading.value = true
    try {
      const to = today.value
      const list = await getSchedule({ from: addDays(to, -(days.value - 1)), to, factoryId })
      if (my !== historySeq) return
      historyItems.value = list.filter((it) => it.date <= to).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time))
    } catch (error) {
      toast(error.message, { type: 'err' })
    } finally {
      if (my === historySeq) loading.value = false
    }
  }

  /** 추가 · 저장 뒤 둘 다 다시 (날짜가 바뀌었을 수 있어 오늘도 다시 셈) */
  function reload() {
    today.value = ymd(serverNow())
    return Promise.all([loadMonth(), loadHistory()])
  }

  return { today, month, days, monthItems, historyItems, loading, loadMonth, loadHistory, reload }
}
