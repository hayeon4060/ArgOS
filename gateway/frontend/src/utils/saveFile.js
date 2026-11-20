/**
 * 파일 저장 (CSV 내보내기에서 씀)
 *
 * 웹 화면은 보안상 저장 경로를 바탕화면으로 직접 정할 수 없음. 그래서
 * - 크롬·엣지: 저장 창(showSaveFilePicker)을 '바탕화면'에서 열고 파일 이름을 미리 채움 → '저장'만 누르면 바탕화면
 * - 저장 창이 없는 브라우저(파이어폭스 등): 브라우저 기본 다운로드 폴더에 저장
 *
 * 저장 창은 '버튼을 누른 그 순간'에만 열 수 있음 → 파일 내용(서버 응답)을 받기 전에 창부터 열고, 내용은 그다음에 씀
 *
 * @param {string} fileName 미리 채울 파일 이름
 * @param {() => Promise<Blob>} makeBlob 파일 내용 만들기 (서버에서 받거나 화면에서 만듦)
 * @returns {Promise<'saved' | 'downloaded' | 'cancelled'>}
 */
export async function saveFile(fileName, makeBlob) {
  if (typeof window.showSaveFilePicker === 'function') {
    let handle
    try {
      handle = await window.showSaveFilePicker({
        suggestedName: fileName,
        startIn: 'desktop',
        types: [{ description: 'CSV 파일', accept: { 'text/csv': ['.csv'] } }],
      })
    } catch (error) {
      if (error.name === 'AbortError') return 'cancelled' // 저장 창에서 '취소'
      throw error
    }
    const blob = await makeBlob()
    const writable = await handle.createWritable()
    await writable.write(blob)
    await writable.close()
    return 'saved'
  }
  // 저장 창이 없는 브라우저: 다운로드 폴더로
  downloadBlob(fileName, await makeBlob())
  return 'downloaded'
}

/**
 * 받은 파일(Blob)을 저장 창 없이 브라우저 다운로드 폴더로 바로 저장
 * - 점검 관리의 빈 서류 양식 · 스캔 원본 PDF 처럼 여러 개를 연달아 받을 때 씀
 *   (저장 창은 버튼을 누른 순간 한 번만 열 수 있어 두 번째 파일부터 막힘)
 */
export function downloadBlob(fileName, blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a) // 파이어폭스는 화면에 붙어 있어야 내려받음
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** 파일 이름에 쓸 수 없는 글자(\ / : * ? " < > |)와 빈칸 빼기 */
export const safeName = (text) => String(text ?? '').replace(/[\\/:*?"<>|\s]/g, '')
