/**
 * 수기 서류 스캔 원본 내려받기 (점검 이력 줄의 내려받기 버튼 · 스캔 원본 미리보기 창)
 * - 실제: 기존 GET /inspections/{inspectionId}/file (로그인 토큰이 필요해 Blob 으로 받아 저장)
 * - 예시 모드: 스캔 파일이 없어 안내만
 */
import { getInspectionFile } from '@/api/inspections'
import { USE_MOCK } from '@/api/mock'
import { toast } from '@/utils/toast'

/** @param {{ inspectionId: number, scanName: string }} item 점검 이력 한 줄 (수기) */
export async function downloadScan(item) {
  if (USE_MOCK) {
    toast(`${item.scanName} 을 내려받았습니다. (예시 모드라 실제 파일은 없습니다)`)
    return
  }
  try {
    const f = await getInspectionFile(item.inspectionId)
    const a = document.createElement('a')
    a.href = f.url
    a.download = item.scanName || `scan_${item.inspectionId}`
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(f.revoke, 1000)
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}
