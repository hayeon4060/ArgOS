/**
 * 대시보드 API (설계 자료 API 명세서 B1 '울산공장 현황' 기준)
 * - GET /sites/{siteId}/overview : 지도·공장 카드·센서 종류별 도넛
 * - GET /alarms                  : 오늘 알림 내역 (오늘 0시부터 누적 전부, 최근 순)
 * - GET /alarms/hourly           : 전일 시간대별 알림 (date=어제, 화면은 하루 00~24시 한 줄)
 */
import { serverNow } from './clock'
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { mockAlarms, mockSiteTrees, mockSites } from './mock/data'
import { factoryLevels, sumLevels } from '@/features/site/siteStats'
import { formatYmd } from '@/utils/format'

const SENSOR_TYPES = ['CURRENT', 'TEMP', 'CO']

/**
 * 예시 데이터로 사업장 현황 응답 만들기
 * - 사업장 펼치기(tree) 예시 데이터를 그대로 써서 사업장 선택 화면과 숫자가 항상 같게 함
 */
function mockOverview(siteId) {
  // 부를 때마다 지금 예시 상황으로 만듦 (api/mock/scenario.js)
  const site = mockSites().find((s) => s.id === siteId)
  const tree = mockSiteTrees()[siteId]
  if (!site || !tree) return null

  // 사업장 전체 센서 종류별 개수 = 공장들의 같은 종류 개수 합
  const levelsByType = Object.fromEntries(
    SENSOR_TYPES.map((type) => [type, sumLevels(tree.factories.map((f) => f.levelsByType[type]))]),
  )

  return {
    site: { id: site.id, name: site.name, level: site.level, mapUrl: null },
    levelsByType,
    factories: tree.factories.map((f) => {
      const counts = factoryLevels(f)
      return {
        id: f.id,
        code: f.code,
        name: f.name,
        level: f.level,
        levelsByType: f.levelsByType,
        sensorCount: counts.NORMAL + counts.WARN + counts.ALARM,
        todayAlarmCount: f.todayAlarmCount,
        processes: f.processes.map(({ id, name, level }) => ({ id, name, level })),
      }
    }),
  }
}

/** 사업장 현황 */
export function getSiteOverview(siteId) {
  const id = Number(siteId)
  if (USE_MOCK) {
    const overview = mockOverview(id)
    return overview ? mockOk(overview) : mockFail({ code: 'NOT_FOUND', message: '사업장을 찾을 수 없습니다.', status: 404 })
  }
  return http.get(`/sites/${id}/overview`)
}

/**
 * 범위(사업장 또는 공장)에 맞는 예시 알림 고르기
 * - 명세서: 범위는 siteId · factoryId · processId 중 하나만 보냄
 */
function mockAlarmsOf({ siteId, factoryId, processId }) {
  const all = mockAlarms()
  if (processId) return all.filter((a) => a.processId === Number(processId))
  if (factoryId) return all.filter((a) => a.factoryId === Number(factoryId))
  if (siteId) return all.filter((a) => a.siteId === Number(siteId))
  return all // 범위 없음: 본사 관리자의 모든 사업장 (사업장 선택 화면의 알림 종 목록)
}

/** 범위 파라미터: 공정 > 공장 > 사업장 순서로 하나만 */
const scopeParams = ({ siteId, factoryId, processId }) =>
  processId ? { processId } : factoryId ? { factoryId } : { siteId }

/**
 * 오늘 알림 목록 (최근 순)
 * - 2026-11-19: 카드가 '오늘 알림 내역'(당일 하루 누적)으로 바뀌어 기본 개수를 20 → 500 (하루 치를 모두 받음)
 * @param {{ siteId?: number, factoryId?: number, processId?: number, size?: number }} params
 * @returns {Promise<{ items: Array, page: number, size: number, total: number }>}
 */
export function getAlarms({ siteId, factoryId, processId, size = 500 }) {
  if (USE_MOCK) {
    // 예시 데이터에만 있는 범위 값(siteId·factoryId·processId)은 빼고 응답 모양만 남김
    const all = mockAlarmsOf({ siteId, factoryId, processId }).map(
      ({ siteId: _s, factoryId: _f, processId: _p, ...item }) => item,
    )
    return mockOk({ items: all.slice(0, size), page: 0, size, total: all.length })
  }
  return http.get('/alarms', { params: { ...scopeParams({ siteId, factoryId, processId }), date: 'today', size } })
}

/**
 * 예시 알림 목록으로 시간대별 알림 만들기 (주간 07~18시, 야간 19~06시)
 * - 주의 → warn, 경보·화재 → alarm, 조치 완료(CLOSED) → action
 */
function mockHourlyOf(alarms) {
  const shift = () => ({ alarmCount: 0, actionCount: 0, hours: [] })
  const result = { day: shift(), night: shift() }
  for (const a of alarms) {
    const hour = Number(a.raisedAt.slice(11, 13))
    const s = hour >= 7 && hour < 19 ? result.day : result.night
    let row = s.hours.find((h) => h.hour === hour)
    if (!row) s.hours.push((row = { hour, warn: 0, alarm: 0, action: 0 }))
    if (a.level === 'WARN') row.warn += 1
    else row.alarm += 1
    s.alarmCount += 1
    if (a.status === 'CLOSED') {
      row.action += 1
      s.actionCount += 1
    }
  }
  return result
}

/**
 * 주간·야간 두 칸 응답을 하루 한 줄로 합치기 (2026-11-20 재원님 요청: 주간/야간 구분 없이 하루 0~24시)
 * - 응답에 hours 가 바로 있으면(백엔드 반영 후) 그대로 씀, 없으면 day + night 를 합침
 * - TODO(백엔드): 지금 집계 범위는 date 07시 ~ 다음 날 07시라서 합치면 00~06시 칸은 '오늘' 새벽 값.
 *   date 00시 ~ 24시 범위로 바꾸고 alarmCount · actionCount · hours 를 바로 달라고 요청함
 *   → docs/api/hourly-alarm-one-day.md
 * @returns {{ alarmCount: number, actionCount: number, hours: Array<{ hour, warn, alarm, action }> }}
 */
function toOneDay(res) {
  if (Array.isArray(res.hours)) return res
  const { day, night } = res
  return {
    alarmCount: day.alarmCount + night.alarmCount,
    actionCount: day.actionCount + night.actionCount,
    hours: [...day.hours, ...night.hours],
  }
}

/**
 * 전일 시간대별 알림 (하루 00~24시 한 줄)
 * - 2026-11-18 화면 제목이 '전일 시간대별 알림'으로 바뀌어 date 를 오늘 → 어제(서버 시연 시계 기준)로 보냄
 * - 서버 응답은 아직 주간(07~19시)·야간(19~07시) 두 칸 → toOneDay 로 합쳐서 돌려줌
 * - 예시 데이터 모드: 어제 예시가 따로 없어 오늘 예시 알림으로 그대로 그림 (서버와 같은 두 칸 모양으로 만들어 같은 길로 합침)
 * @param {{ siteId?: number, factoryId?: number }} params
 * @returns {Promise<{ alarmCount, actionCount, hours }>}
 */
export function getHourlyAlarms({ siteId, factoryId }) {
  if (USE_MOCK) return mockOk(mockHourlyOf(mockAlarmsOf({ siteId, factoryId }))).then(toOneDay)
  const yesterday = serverNow()
  yesterday.setDate(yesterday.getDate() - 1)
  return http
    .get('/alarms/hourly', { params: { ...scopeParams({ siteId, factoryId }), date: formatYmd(yesterday) } })
    .then(toOneDay)
}
