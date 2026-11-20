/**
 * 점검 관리(수기 서류 OCR) API (설계 자료 API 명세서 E1, 백엔드 InspectionController)
 * - GET  /inspections/summary?month=&factoryId= : 이번 달 이행률, 서류별 건수, 달력 점
 * - GET  /doc-forms                             : 서류 양식(F01~F05) 칸 목록
 * - POST /inspections (multipart)               : 서류 올리기 → 202 { id, status: RECOGNIZING }
 * - GET  /inspections/{id}/ocr                  : 인식 진행·결과 (REVIEW 가 될 때까지 2초마다)
 * - GET  /inspections/{id}/file?page=           : 스캔 원본 그림
 * - PUT  /inspections/{id}                      : 임시 저장(REVIEW) · 검수 완료(DONE)
 * - POST /inspections/{id}/ocr                  : 다시 인식
 * - GET  /inspections?from=&to=&formType=&status= : 등록 목록
 * - GET  /inspections/schedule?date=&factoryId=   : 그날 점검 목록 (달력 날짜를 눌렀을 때)
 *
 * 글자 인식(네이버 CLOVA OCR 템플릿)은 백엔드가 부르고 키도 백엔드에만 있음 → 프론트에는 OCR 키·주소를 두지 않음
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import * as mock from './mock/inspectionData'

const clean = (obj) => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== '' && v != null))
/** 예시 결과 { data } / { error } → 성공·실패 응답 흉내 */
const reply = (r) => (r.error ? mockFail(r.error) : mockOk(r.data))

/** 올릴 수 있는 파일 (명세서: PDF · JPG · PNG, 20MB 이하) */
export const FILE_ACCEPT = '.pdf,.jpg,.jpeg,.png'
export const FILE_MAX = 20 * 1024 * 1024

export function getInspectionSummary({ month, factoryId } = {}) {
  if (USE_MOCK) return mockOk(mock.summary(month))
  return http.get('/inspections/summary', { params: clean({ month, factoryId }) })
}

export function getDocForms() {
  if (USE_MOCK) return mockOk(mock.docForms())
  return http.get('/doc-forms')
}

/**
 * 서류 올리기
 * @param {File|{ name: string }} file 예시 모드에서는 이름만 씀
 * @param {object} o { formType, processId, scheduleId, onProgress(0~100), by(예시 기록용 이름) }
 */
export async function uploadInspection(file, { formType, processId, scheduleId, onProgress, by } = {}) {
  if (USE_MOCK) {
    // 예시: 올리는 진행률을 0.1초마다 10%씩
    for (let p = 0; p <= 100; p += 20) {
      onProgress?.(p)
      await new Promise((r) => setTimeout(r, 100))
    }
    return mockOk(mock.upload({ fileName: file.name, formType, by }))
  }
  const form = new FormData()
  form.append('file', file)
  Object.entries(clean({ formType, processId, scheduleId })).forEach(([k, v]) => form.append(k, v))
  return http.post('/inspections', form, {
    onUploadProgress: (e) => e.total && onProgress?.(Math.round((e.loaded / e.total) * 100)),
  })
}

/** @returns {{ id, status, progress, formType, pageCount, fields: [{ key, label, value, box, needCheck, reason }], needCheckCount }} */
export function getInspectionOcr(id) {
  if (USE_MOCK) return reply(mock.ocr(id))
  return http.get(`/inspections/${id}/ocr`)
}

/**
 * 스캔 원본 그림 주소
 * - 실제: 로그인 토큰이 필요해 그림을 받아 브라우저 안 주소(blob:)로 만듦 → 다 쓰면 URL.revokeObjectURL
 * @returns {Promise<{ url: string|null, revoke: () => void }>}
 */
export async function getInspectionFile(id, page = 1) {
  if (USE_MOCK) return mockOk({ url: mock.fileUrl(id), revoke: null }).then((r) => ({ url: r.url, revoke: () => {} }))
  const blob = await http.get(`/inspections/${id}/file`, { params: { page }, responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  return { url, revoke: () => URL.revokeObjectURL(url) }
}

/** 임시 저장(status REVIEW) · 검수 완료 저장(status DONE) */
export function saveInspection(id, body, by) {
  if (USE_MOCK) return reply(mock.save(id, body, by))
  return http.put(`/inspections/${id}`, body)
}

/** 다시 인식 */
export function reocrInspection(id) {
  if (USE_MOCK) return reply(mock.reocr(id))
  return http.post(`/inspections/${id}/ocr`)
}

/** 등록 목록 { items, page, size, total } (page 는 0부터) */
export function getInspections(params = {}) {
  if (USE_MOCK) return mockOk(mock.list(params))
  return http.get('/inspections', { params: clean(params) })
}

/**
 * 그날 점검 목록 (명세서에 없음 — backend-notes 13장 요청으로 백엔드가 추가)
 * @param {string} date 'YYYY-MM-DD'
 * @param {number} [factoryId] 공장 담당자는 자기 공장 (없으면 담당 범위 전체)
 * @returns {Promise<Array<{ scheduleId, date, time, title, formType, formName, processId, processName,
 *   equipmentId, equipmentName, assignee, status: 'DONE'|'PLANNED'|'DELAYED', doneAt, doneBy }>>}
 */
export function getDaySchedule(date, factoryId) {
  if (USE_MOCK) return mockOk(mock.daySchedule(date))
  return http.get('/inspections/schedule', { params: clean({ date, factoryId }) })
}
