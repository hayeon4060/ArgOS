/**
 * 알림 대응 상태 (Pinia)
 * - 지금 대응 중인 알림·진행 중 체크리스트 (상단 바 상태 띠, 체크리스트 진행, 공정 화면 칩이 같이 씀)
 * - 지금 열린 알림 창 (주의 알림 / 주의 상세 / 체크리스트 진행)
 * - 불러오기: AlertHost 가 처음 한 번 + 데이터가 바뀔 때마다(useLiveReload) refresh()
 * - 권한: 주의 확인·전송·조치 확인은 공장 담당자만. 본사 관리자는 상태 띠·알림 띠로 보고, 체크리스트 진행 창은 열람만
 *   (화면설계서 C1 '본사 관리자는 알림 띠로만', C3 '본사 관리자는 열람')
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getActiveAlarms } from '@/api/alarms'
import { getActiveChecklists } from '@/api/checklists'
import { useAuthStore } from './auth'
import { placeOf } from '@/features/alert/alarmPlace'
import { LEVEL, ROLE } from '@/types/codes'

/** 단계 높은 순서 (상태 띠에 대표로 보여 줄 알림 고르기) */
const RANK = { [LEVEL.FIRE]: 3, [LEVEL.ALARM]: 2, [LEVEL.WARN]: 1, [LEVEL.NORMAL]: 0 }

export const useAlertStore = defineStore('alert', () => {
  const auth = useAuthStore()

  /** 대응 중인 알림 (GET /alarms?status=OPEN,ACKED,ACTING) */
  const activeAlarms = ref([])
  /** 진행 중 체크리스트 (GET /checklists?status=ACTIVE) */
  const checklists = ref([])
  /** 열린 창: { type: 'warn'|'detail'|'checklist'|'alarm', alarmId, checklistId } 또는 null */
  const modal = ref(null)

  /** 주의 확인·전송·조치 확인을 할 수 있는 사람 (공장 담당자) */
  const canAct = computed(() => auth.role === ROLE.PLANT_ADMIN)

  /** 대응 중인 알림을 단계 높은 순(같으면 최근 순)으로 — 상단 바 상태 띠의 목록 */
  const sortedAlarms = computed(() =>
    [...activeAlarms.value].sort((a, b) => RANK[b.level] - RANK[a.level] || b.raisedAt.localeCompare(a.raisedAt)),
  )

  /** 상태 띠에 보여 줄 대표 알림 (가장 높은 단계, 같으면 최근) */
  const primary = computed(() => sortedAlarms.value[0] ?? null)

  /** 담당 범위 (공장 담당자는 담당 공장만) */
  const scope = () => {
    const factoryId = auth.user?.scope?.factoryIds?.[0]
    return auth.role === ROLE.PLANT_ADMIN && factoryId ? { factoryId } : {}
  }

  async function refresh() {
    if (!auth.isLoggedIn) return
    try {
      const [alarms, lists] = await Promise.all([getActiveAlarms(scope()), getActiveChecklists(scope())])
      activeAlarms.value = alarms
      checklists.value = lists
    } catch {
      // 못 받아도 화면은 그대로 (다음 갱신 때 다시)
    }
  }

  /** 알림의 공정 이름 ('도장 공정') — 체크리스트 진행 칸·창 (체크리스트 응답에는 공정 번호만 있음) */
  const processNameOf = (alarmId) => placeOf(activeAlarms.value.find((a) => a.id === alarmId)).processName

  /**
   * 설비의 진행 중 체크리스트 (공정 화면 칩, 설비 상세 배지)
   * - 서버 목록 줄에는 equipmentId 가 없어서, 없으면 그 체크리스트의 알림(alarmId)에서 설비를 찾음
   */
  const equipmentOfChecklist = (c) => c.equipmentId ?? activeAlarms.value.find((a) => a.id === c.alarmId)?.equipmentId
  const checklistOfEquipment = (equipmentId) => checklists.value.find((c) => equipmentOfChecklist(c) === equipmentId) ?? null

  function open(type, ids = {}) {
    modal.value = { type, ...ids }
  }

  function close() {
    modal.value = null
  }

  /**
   * 알림에 맞는 창 열기 (상태 띠·알림 줄 '주의 상세 열기')
   * - 경보·화재 → 경보 대응 창 (본사 관리자도 열람으로 열림)
   * - 주의: 확인 전 → 주의 알림(2초 길게) / 확인함 → 주의 상세 / 조치 중 → 체크리스트 진행
   * - 본사 관리자는 주의 쪽은 체크리스트 진행(열람)만 열 수 있음
   * @returns {boolean} 열었는지
   */
  function openFor(alarm) {
    if (!alarm) return false
    if (alarm.level === LEVEL.ALARM || alarm.level === LEVEL.FIRE) {
      // 화재가 있으면 화재 창, 아니면 가장 최근 경보 — 경보 줄을 눌러도 대표 건 하나로 엶 (같은 설비의 경보는 한 창에서 대응)
      const latest = (level) =>
        activeAlarms.value.filter((a) => a.level === level).sort((a, b) => b.raisedAt.localeCompare(a.raisedAt))[0]
      const main = latest(LEVEL.FIRE) ?? latest(LEVEL.ALARM) ?? alarm
      open('alarm', { alarmId: main.id })
      return true
    }
    if (alarm.status === 'ACTING' && alarm.checklist) {
      open('checklist', { alarmId: alarm.id, checklistId: alarm.checklist.id })
      return true
    }
    if (!canAct.value) return false
    open(alarm.status === 'OPEN' ? 'warn' : 'detail', { alarmId: alarm.id })
    return true
  }

  /** 로그아웃 때 비우기 */
  function reset() {
    activeAlarms.value = []
    checklists.value = []
    modal.value = null
  }

  return { activeAlarms, sortedAlarms, checklists, modal, canAct, primary, refresh, processNameOf, checklistOfEquipment, open, close, openFor, reset }
})
