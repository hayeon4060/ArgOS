/**
 * 사업장 펼치기용 공장 목록 불러오기 (GET /sites/{siteId}/tree)
 * - 사업장마다 처음 펼칠 때 한 번만 불러오고, 다음부터는 저장된 값을 씀
 * - 상황(정상·주의 등)이 바뀌면 이미 펼쳐 본 사업장만 조용히 다시 불러옴 (useLiveReload)
 * - trees[siteId] = { loading, error, factories }
 */
import { ref } from 'vue'
import { getSiteTree } from '@/api/sites'
import { factoryLevels } from './siteStats'
import { useLiveReload } from '@/utils/useLiveReload'

export function useSiteTrees() {
  const trees = ref({})

  /** force: 이미 불러온 사업장도 다시 (펼친 모양은 그대로 두고 값만 바꿈) */
  async function loadTree(siteId, { force = false } = {}) {
    const saved = trees.value[siteId]
    if (!force && (saved?.factories || saved?.loading)) return
    if (!saved?.factories) trees.value[siteId] = { loading: true, error: '', factories: null }
    try {
      const tree = await getSiteTree(siteId)
      trees.value[siteId] = {
        loading: false,
        error: '',
        // 공장 줄의 센서 개수 = 전류·온도·CO 개수 합
        factories: tree.factories.map((f) => ({ ...f, counts: factoryLevels(f) })),
      }
    } catch (error) {
      trees.value[siteId] = { loading: false, error: error.message, factories: null }
    }
  }

  useLiveReload(() => {
    Object.keys(trees.value).forEach((id) => loadTree(id, { force: true }))
  })

  return { trees, loadTree }
}
