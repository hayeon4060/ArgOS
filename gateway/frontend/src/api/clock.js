/**
 * 시연 시계 (서버의 "지금")
 * - 서버는 시연 시계를 씀: 서버가 켜진 순간 = 2026-11-16 09:00 (GET /system/clock)
 * - PC 시계로 '오늘', '최근 1시간'을 계산하면 서버 데이터와 날짜가 어긋나므로
 *   로그인 뒤 한 번 서버 시각을 받아 PC 시계와의 차이(offset)를 기억하고, serverNow() 로 씀
 * - 예시 데이터 모드에서는 PC 시계 그대로 (offset 0)
 */
import http from './http'
import { USE_MOCK } from './mock'

let offsetMs = 0

/** 서버 기준 지금 (Date) */
export function serverNow() {
  return new Date(Date.now() + offsetMs)
}

/** 서버 시각을 받아 차이를 맞춤. 실패하면 PC 시계 그대로 둠 */
export async function syncServerClock() {
  if (USE_MOCK) return
  try {
    const started = Date.now()
    const { now } = await http.get('/system/clock')
    const rtt = Date.now() - started
    offsetMs = new Date(now).getTime() + rtt / 2 - Date.now()
  } catch {
    // 시계를 못 받아도 화면은 계속 동작 (PC 시계 사용)
  }
}
