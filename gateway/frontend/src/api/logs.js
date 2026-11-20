/**
 * 이용 로그 API (API 명세서 G2, 화면설계서 G2)
 * - GET /logs        : 표 + 유형별 건수 + 시간대별 이용을 한 번에
 * - GET /logs/export : 같은 조건의 CSV (내려받은 것도 로그에 남음)
 * - 백엔드 log/LogController (activity_log + 로그인 기록 login_history). from · to 는 필수
 * - 유형(type)은 화면의 한 칸이 여러 코드일 수 있어 쉼표로 이어 보냄 ('LOGIN,LOGOUT') — 서버도 쉼표로 받음
 */
import http from './http'
import { USE_MOCK, mockOk } from './mock'
import { exportLogs as mockExport, listLogs } from './mock/logData'
import { useAuthStore } from '@/stores/auth'

/** 빈 조건은 빼고 보냄 */
const clean = (c) => Object.fromEntries(Object.entries(c).filter(([, v]) => v !== '' && v != null))

/**
 * @param {{ from, to, factoryId?, processId?, type?, q?, page, size }} c  날짜는 'YYYY-MM-DD', page 는 0부터
 * @returns {{ items, countsByType, byHour, topUsers?, page, size, total }}
 */
export function getLogs(c) {
  if (USE_MOCK) return mockOk(listLogs(useAuthStore().user, c))
  return http.get('/logs', { params: clean(c) })
}

/**
 * CSV 내려받기 → { blob, count? }
 * @param {object} c getLogs 와 같은 조건 (page · size 없이)
 */
export async function exportLogs(c) {
  if (USE_MOCK) {
    const { text, count } = await mockOk(mockExport(useAuthStore().user, c))
    return { blob: new Blob([text], { type: 'text/csv;charset=utf-8' }), count }
  }
  const blob = await http.get('/logs/export', { params: clean(c), responseType: 'blob' })
  return { blob, count: null }
}
