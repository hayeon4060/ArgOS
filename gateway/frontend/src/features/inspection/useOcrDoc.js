/**
 * 지금 보고 있는 서류 한 건의 흐름 (화면설계안 vInspect() 의 upload · recognize · 저장)
 *   ① 파일 올리기 → ② 문자 인식(2초마다 결과 확인) → ③ 원본 대조(확인·고치기) → ④ 저장
 *
 * - stage: 0 올리는 중 / 1 인식 중 / 2 원본 대조 / 4 저장 끝 (설계안과 같은 번호, 3 은 '저장 중' 단추로만 보임)
 * - 다른 서류를 열거나 새로 올리면 이전 확인 요청은 버림 (seq) — 늦게 온 결과가 지금 서류를 덮지 않게
 * - 화면을 떠나면 타이머·원본 그림 주소 정리 (dispose)
 */
import { computed, reactive } from 'vue'
import {
  getInspectionFile,
  getInspectionOcr,
  reocrInspection,
  saveInspection,
  uploadInspection,
} from '@/api/inspections'

const POLL_MS = 2000 // 명세서: REVIEW 가 될 때까지 2초마다

export function useOcrDoc() {
  const doc = reactive({
    id: null,
    fileName: '',
    formType: '',
    stage: 2,
    status: '', // RECOGNIZING | REVIEW | DONE | FAILED
    upload: 0, // 올리는 진행률
    progress: 0, // 인식 진행률
    fields: [], // [{ key, label, value, box, needCheck, reason, checked, edited }]
    imageUrl: null,
    reviewedBy: null,
    reviewedAt: null,
    failMessage: '',
    saving: false,
    scheduleId: null, // 백엔드가 맞춰 준 점검 건 (없으면 null — 화면이 scheduleMatch.js 로 맞춤)
  })

  let seq = 0
  let timer = 0
  let revoke = () => {}

  function stop() {
    seq += 1
    clearTimeout(timer)
  }

  /** 원본 그림 바꾸기 (이전 주소는 정리) */
  async function loadImage(my) {
    revoke()
    revoke = () => {}
    doc.imageUrl = null
    try {
      const f = await getInspectionFile(doc.id)
      if (my !== seq) return f.revoke()
      doc.imageUrl = f.url
      revoke = f.revoke
    } catch {
      doc.imageUrl = null
    }
  }

  /** 인식 결과 확인 (끝날 때까지 반복) */
  async function poll(my) {
    try {
      const r = await getInspectionOcr(doc.id)
      if (my !== seq) return
      doc.status = r.status
      doc.progress = r.progress ?? 0
      doc.formType = r.formType || doc.formType
      if (r.status === 'RECOGNIZING') {
        doc.stage = 1
        timer = setTimeout(() => poll(my), POLL_MS)
        return
      }
      doc.fields = r.fields.map((f) => ({ ...f, checked: false, edited: false }))
      doc.reviewedBy = r.reviewedBy ?? null
      doc.reviewedAt = r.reviewedAt ?? null
      doc.scheduleId = r.scheduleId ?? null
      doc.stage = r.status === 'DONE' ? 4 : 2
      if (!doc.imageUrl) await loadImage(my)
    } catch (error) {
      if (my !== seq) return
      // 422 OCR_FAILED: 글자를 읽지 못함
      doc.status = 'FAILED'
      doc.stage = 1
      doc.failMessage = error.message
    }
  }

  /** 등록 목록의 서류 열기 */
  async function open(row) {
    stop()
    const my = seq
    Object.assign(doc, {
      id: row.id,
      fileName: row.fileName,
      formType: row.formType,
      status: row.status,
      stage: row.status === 'DONE' ? 4 : row.status === 'RECOGNIZING' ? 1 : 2,
      upload: 100,
      progress: row.progress ?? 0,
      fields: [],
      imageUrl: null,
      failMessage: '',
      scheduleId: null,
    })
    await loadImage(my)
    await poll(my)
  }

  /** 새 파일 올리기 */
  async function upload(file, { by, formType } = {}) {
    stop()
    const my = seq
    revoke()
    Object.assign(doc, { id: null, fileName: file.name, formType: '', status: 'UPLOADING', stage: 0, upload: 0, progress: 0, fields: [], imageUrl: null, failMessage: '', scheduleId: null })
    try {
      const r = await uploadInspection(file, { by, formType, onProgress: (p) => my === seq && (doc.upload = p) })
      if (my !== seq) return null
      doc.id = r.id
      doc.status = r.status
      doc.stage = 1
      await loadImage(my)
      poll(my)
      return r
    } catch (error) {
      if (my === seq) Object.assign(doc, { status: 'FAILED', stage: 0, failMessage: error.message })
      throw error
    }
  }

  /** 다시 인식 (고친 값은 지워짐) */
  async function reocr() {
    stop()
    const my = seq
    await reocrInspection(doc.id)
    if (my !== seq) return
    Object.assign(doc, { status: 'RECOGNIZING', stage: 1, progress: 0, fields: [], failMessage: '' })
    poll(my)
  }

  /* ───────── 원본 대조 ───────── */
  const needCheckLeft = computed(() => doc.fields.filter((f) => f.needCheck && !f.checked && !f.edited).length)
  const editedCount = computed(() => doc.fields.filter((f) => f.edited).length)

  /** '확인' 단추: 원본과 같다고 표시 */
  const confirm = (key) => {
    const f = doc.fields.find((x) => x.key === key)
    if (f) f.checked = true
  }
  /** 값 고치기 → 직접 고친 항목 +1 (확인 필요도 풀림) */
  const edit = (key, value) => {
    const f = doc.fields.find((x) => x.key === key)
    if (!f || f.value === value) return
    f.value = value
    f.edited = true
  }

  /**
   * 임시 저장(REVIEW) · 검수 완료(DONE)
   * @param {number|null} [scheduleId] 검수 완료할 때 이을 점검 건 (null = 연결 안 함 → 새 점검 건)
   */
  async function save(status, by, scheduleId = null) {
    doc.saving = true
    try {
      const body = { status, fields: doc.fields.map((f) => ({ key: f.key, value: f.value, checked: f.checked })) }
      if (status === 'DONE') body.scheduleId = scheduleId
      const r = await saveInspection(doc.id, body, by)
      doc.status = r.status
      if (status === 'DONE') {
        doc.stage = 4
        doc.reviewedBy = r.reviewedBy
        doc.reviewedAt = r.reviewedAt
      }
      return r
    } finally {
      doc.saving = false
    }
  }

  function dispose() {
    stop()
    revoke()
  }

  return { doc, open, upload, reocr, confirm, edit, save, needCheckLeft, editedCount, dispose }
}
