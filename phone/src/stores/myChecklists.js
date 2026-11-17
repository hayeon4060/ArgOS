/**
 * 내 체크리스트 (Pinia)
 * - 아래 탭 숫자, 공정 현황 '할 일', 체크리스트 목록, 새 체크리스트 알림 띠가 같이 씀
 * - 불러오기: PhoneLayout 이 처음 한 번 + 데이터가 바뀔 때마다(useLiveReload) refresh()
 * - reminds: 진행 중 체크리스트별 '다시 알림' 횟수 — 목록 API 에 없어서 한 건 보기(GET /checklists/{id})로 받음
 *   (보통 1~2건이라 부담 없음. 실제 앱에서는 FCM 푸시가 새 체크리스트·다시 알림을 직접 알려 줌)
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getChecklist } from '@/api/checklists'
import { useAuthStore } from '@/stores/auth'
import { getMyChecklists } from '../api/myChecklists'

export const useMyChecklistsStore = defineStore('myChecklists', () => {
  const auth = useAuthStore()

  /** 진행 중 (SENT · IN_PROGRESS): [{ id, status, level, title, sentAt, doneCount, totalCount }] */
  const active = ref([])
  /** 끝난 것 (REPORTED · CONFIRMED · CANCELLED) */
  const done = ref([])
  /** { 체크리스트 id: 다시 알림 횟수 } (진행 중인 것만) */
  const reminds = ref({})
  const loaded = ref(false)

  async function refresh() {
    if (!auth.isLoggedIn) return
    const userId = auth.user.id
    try {
      const [a, d] = await Promise.all([getMyChecklists({ status: 'ACTIVE', userId }), getMyChecklists({ status: 'DONE', userId })])
      const details = await Promise.all(a.map((c) => getChecklist(c.id)))
      active.value = a
      done.value = d
      reminds.value = Object.fromEntries(details.map((c) => [c.id, c.remindCount]))
      loaded.value = true
    } catch {
      // 못 받아도 화면은 그대로 (다음 갱신 때 다시)
    }
  }

  /** 로그아웃 때 비우기 */
  function reset() {
    active.value = []
    done.value = []
    reminds.value = {}
    loaded.value = false
  }

  return { active, done, reminds, loaded, refresh, reset }
})
