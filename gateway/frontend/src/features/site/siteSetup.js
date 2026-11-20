/**
 * 아직 아무것도 설정하지 않은 사업장인지 (2026-11-20 재원님 요청: 새 사업장은 상단 메뉴에 '관리'만)
 * - 사업장별 공장 수를 한 번 불러와 기억함 (GET /sites/{siteId}/tree — 본사 관리자 · 공장 담당자 모두 부를 수 있음)
 * - 공장 관리에서 공장을 추가하면 setFactoryCount 로 바로 고쳐서 메뉴가 다시 나타남
 * - 상단 메뉴(AppNav) · 사업장 선택(SiteSelectPage)이 씀
 */
import { reactive } from 'vue'
import { getSiteTree } from '@/api/sites'

/** { 사업장 id: 공장 수 } — 아직 모르면 값 없음 */
const factoryCount = reactive({})

/**
 * 설정 전 사업장 기준: 공장이 하나도 없음
 * (기준을 바꾸려면 이 한 줄만 고치면 됨. 모르는 사업장은 '설정됨'으로 봐서 메뉴가 깜빡이지 않게)
 */
export const isEmptySite = (siteId) => factoryCount[siteId] === 0

/** 공장 수를 알게 됐을 때 (사업장 추가 직후 0, 공장 추가 후 늘어난 수) */
export function setFactoryCount(siteId, count) {
  factoryCount[siteId] = count
}

/** 처음 보는 사업장이면 공장 수 불러오기 (실패하면 모르는 채로 둠 → 메뉴 그대로) */
export async function checkSite(siteId) {
  if (!siteId || siteId in factoryCount) return
  try {
    const tree = await getSiteTree(siteId)
    factoryCount[siteId] = tree.factories.length
  } catch {
    // 메뉴를 숨기지 않음
  }
}
