/**
 * 점검 관리 API (설계 자료 API 명세서 E1, 백엔드 InspectionController)
 *
 * [수기 서류 등록 (OCR) — 백엔드에 있음]
 * - GET  /doc-forms                             : 서류 양식(F01~F05) 칸 목록
 * - POST /inspections (multipart)               : 서류 올리기 → 202 { id, status: RECOGNIZING }
 * - GET  /inspections/{id}/ocr                  : 인식 진행·결과 (REVIEW 가 될 때까지 2초마다)
 * - GET  /inspections/{id}/file?page=           : 스캔 원본 그림
 * - PUT  /inspections/{id}                      : 임시 저장(REVIEW) · 검수 완료(DONE)
 * - POST /inspections/{id}/ocr                  : 다시 인식
 * - GET  /inspections?from=&to=&formType=&status= : 등록 목록
 *
 * [점검 스케줄 · 이력 · 전자 서류 양식 — 2026-11-20 개편안 A안, 백엔드에 요청 (docs/api/inspection-redesign-api.md)]
 * - GET  /inspections/schedule?month= | from=&to=   : 점검 건 목록 (달력 · 이력 · 월별 보고서가 모두 이 목록을 씀)
 * - GET  /inspections/schedule/options              : 스케줄 추가 창의 설비 · 점검자 목록
 * - POST /inspections/schedule                      : 스케줄 추가 (반복 포함)
 * - GET  /inspections/schedule/{scheduleId}/form    : 그 건의 서류 양식 값
 * - PUT  /inspections/schedule/{scheduleId}/form    : 예정 · 누락 건을 전자 작성으로 완료
 * - POST /inspections/records                       : 스케줄 없이 '점검일지 작성'으로 새로 작성
 * - GET  /doc-forms/{formType}/template             : 빈 서류 양식 PDF
 * - 스캔 원본은 기존 GET /inspections/{inspectionId}/file 을 그대로 씀 (이력 줄의 inspectionId)
 *
 * 글자 인식(네이버 CLOVA OCR 템플릿)은 백엔드가 부르고 키도 백엔드에만 있음 → 프론트에는 OCR 키·주소를 두지 않음
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import * as mock from './mock/inspectionData'
import * as sched from './mock/inspectionSchedule'

const clean = (obj) => Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== '' && v != null))
/** 예시 결과 { data } / { error } → 성공·실패 응답 흉내 */
const reply = (r) => (r.error ? mockFail(r.error) : mockOk(r.data))

/** 올릴 수 있는 파일 (명세서: PDF · JPG · PNG, 20MB 이하) */
export const FILE_ACCEPT = '.pdf,.jpg,.jpeg,.png'
export const FILE_MAX = 20 * 1024 * 1024

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
 * - type: 파일 종류 (백엔드는 PDF 를 그림으로 바꾸지 않고 PDF 그대로 줌 → 점검 이력 미리보기가 PDF 면 iframe 으로)
 * @returns {Promise<{ url: string|null, type: string, revoke: () => void }>}
 */
export async function getInspectionFile(id, page = 1) {
  if (USE_MOCK) return mockOk({ url: mock.fileUrl(id), revoke: null }).then((r) => ({ url: r.url, type: 'image/jpeg', revoke: () => {} }))
  const blob = await http.get(`/inspections/${id}/file`, { params: { page }, responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  return { url, type: blob.type, revoke: () => URL.revokeObjectURL(url) }
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

/* ═════════════════ 점검 스케줄 · 이력 · 전자 서류 양식 (개편안 A안) ═════════════════ */

/**
 * 점검 건 목록 (스케줄 한 줄 = 점검 한 건)
 * - 달력: { month: 'YYYY-MM' } / 이력: { from, to } (오늘 기준 최근 N일)
 * - TODO(백엔드): 지금 GET /inspections/schedule 은 date(하루)만 받음 → month · from&to 도 받고, 아래 칸을 넣어 주세요
 * @returns {Promise<Array<{ scheduleId, date, time, formType, equipmentId, equipmentName, equipmentNo,
 *   assignee, status: 'DONE'|'PLANNED'|'DELAYED', source: 'PAPER'|'DIGITAL'|null, result: 'OK'|'NG'|null,
 *   inspectionId, scanName, doneAt, doneBy }>>}
 */
export function getSchedule({ month, from, to, factoryId } = {}) {
  if (USE_MOCK) return mockOk(sched.list({ month, from, to }))
  return http.get('/inspections/schedule', { params: clean({ month, from, to, factoryId }) })
}

/**
 * 스케줄 추가 창의 고르는 칸 { equipments: [{ id, name, code, formType }], inspectors: [{ id, name }] }
 * - TODO(백엔드): 새 API. 공장 담당자는 자기 공장 설비 · 사용자만
 */
export function getScheduleOptions(factoryId) {
  if (USE_MOCK) return mockOk(sched.options())
  return http.get('/inspections/schedule/options', { params: clean({ factoryId }) })
}

/**
 * 스케줄 추가 (반복이면 여러 건) → 만든 건 목록
 * @param {{ date, time, formType, equipmentId, assigneeId, repeat: 'ONCE'|'DAILY'|'WEEKLY'|'MONTHLY', count }} body
 * - TODO(백엔드): 새 API (지금은 조회만 있음)
 */
export function addSchedule(body) {
  if (USE_MOCK) return reply(sched.add(body))
  return http.post('/inspections/schedule', body)
}

/**
 * 그 건의 서류 양식 값 { scheduleId, formType, source, values: { inspectedAt, inspType, item01, ... } }
 * - values 의 키는 GET /doc-forms 의 fields.key 와 같음 → 수기(OCR 결과)든 전자 작성이든 같은 양식에 채움
 * - TODO(백엔드): 새 API. 수기 건은 ocr_result.fields(검수 완료 값), 전자 건은 저장한 값
 */
export function getScheduleForm(scheduleId) {
  if (USE_MOCK) return reply(sched.form(scheduleId))
  return http.get(`/inspections/schedule/${scheduleId}/form`)
}

/**
 * 예정 · 누락 건을 서류 양식으로 작성해 완료 (전자) → 바뀐 점검 건
 * @param {{ values: object, result: 'OK'|'NG' }} body
 * - TODO(백엔드): 새 API. 저장하면 status DONE · source DIGITAL · done_at 지금
 */
export function saveScheduleForm(scheduleId, body) {
  if (USE_MOCK) return reply(sched.saveForm(scheduleId, body))
  return http.put(`/inspections/schedule/${scheduleId}/form`, body)
}

/**
 * 스케줄에 없던 점검을 '점검일지 작성'으로 새로 작성 → 만든 점검 건 (완료 · 전자)
 * @param {{ formType, equipmentId, date, time, values, result }} body
 * - TODO(백엔드): 새 API
 */
export function createInspectionRecord(body) {
  if (USE_MOCK) return reply(sched.createRecord(body))
  return http.post('/inspections/records', body)
}

/**
 * 빈 서류 양식 PDF (현장에서 인쇄해 손으로 쓰는 용도) → Blob
 * - 예시 모드에는 파일이 없어 null
 * - TODO(백엔드): 새 API. 팀 양식 PDF(ArgOS_서류양식.pdf)의 그 쪽만
 */
export async function getFormTemplate(formType) {
  if (USE_MOCK) return mockOk(null)
  return http.get(`/doc-forms/${formType}/template`, { responseType: 'blob' })
}
