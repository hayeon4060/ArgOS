/**
 * 사업장·공장 API (설계 자료 API 명세서 A2 기준)
 * - 주소 이름 규칙: 사업장 /sites, 공장 /factories (DB 의 plant 테이블 = 사업장)
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { addSite } from './mock/addedScope'
import { mockSiteTrees, mockSites } from './mock/data'

/** 사업장 목록: 지도·도넛·목록 머리줄 (GET /sites) */
export function getSites() {
  if (USE_MOCK) return mockOk(mockSites()) // 지금 예시 상황으로 만듦
  return http.get('/sites')
}

/** 사업장 펼치기: 공장 → 공정 → 이상 센서 (GET /sites/{siteId}/tree) */
export function getSiteTree(siteId) {
  if (USE_MOCK) {
    const tree = mockSiteTrees()[siteId]
    return tree ? mockOk(tree) : mockFail({ code: 'NOT_FOUND', message: '사업장을 찾을 수 없습니다.', status: 404 })
  }
  return http.get(`/sites/${siteId}/tree`)
}

/**
 * 사업장 이름 (상단 바의 사업장 표시용)
 * - 명세서에 이름만 주는 API 가 없어서, 실제 연결 시에는 사업장 현황 응답의 site.name 을 씀
 *   (공장 담당자는 GET /sites 를 못 부르므로 overview 사용)
 * - TODO: 백엔드와 '이름만 주는 가벼운 API' 또는 GET /me 의 scope.siteName 사용 여부 합의
 * @returns {Promise<string>}
 */
export async function getSiteName(siteId) {
  const id = Number(siteId)
  if (USE_MOCK) {
    const site = mockSites().find((s) => s.id === id)
    return site ? mockOk(site.name) : mockFail({ code: 'NOT_FOUND', message: '사업장을 찾을 수 없습니다.', status: 404 })
  }
  const overview = await http.get(`/sites/${id}/overview`)
  return overview.site.name
}

/**
 * 코드 자동 만들기 (사업장 · 공장 · 공정 추가)
 * - 백엔드는 code 를 꼭 받지만(DB varchar(10), 같은 상위 안에서 겹치면 409) 화면에서는 이름만 입력받음
 * - 머리글자 + 지금 시각(36진수) 끝 6자리 → 예: F1K3Z9Q (7자)
 * - TODO(백엔드: code 를 비워 보내면 서버가 만들어 주면 이 함수는 지움 (docs/api/scope-add.md)
 * @param {'S'|'F'|'P'} head 사업장 S, 공장 F, 공정 P
 */
export const autoCode = (head) => head + Date.now().toString(36).toUpperCase().slice(-6)

/**
 * 사업장 추가 (POST /sites, 본사 관리자만) — 공장 관리 '사업장 추가'
 * - 지금은 이름만 보냄 (주소 · 위도 · 경도는 비움 → 사업장 선택 지도에 핀이 안 찍힘)
 * @returns {Promise<{ id, name }>}
 */
export function createSite({ name }) {
  const body = { name, code: autoCode('S') }
  if (USE_MOCK) {
    const r = addSite(body, mockSites().map((s) => s.name))
    return r.error ? mockFail(r.error) : mockOk(r.data)
  }
  return http.post('/sites', body)
}
