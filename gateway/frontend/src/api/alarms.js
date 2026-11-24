/**
 * 알림 대응 API — 알림 (설계 자료 API 명세서 C1, 백엔드 gateway/backend .../alarm/AlarmController)
 * - GET  /alarms?status=OPEN,ACKED,ACTING : 지금 대응 중인 알림 (상단 바 상태 띠, 주의 창 자동 열기)
 * - GET  /alarms/{alarmId}                : 조치 내역 (오늘 알림 내역 줄 펼치기)
 * - GET  /alarms/{alarmId}/detail         : 주의 상세 (원인, 그때 센서값, 열화상 1장, 추이, 원인 후보)
 * - POST /alarms/{alarmId}/ack            : 주의 확인 (2초 길게 누르기)
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { THERMAL_BY_STATE } from './mock/equipmentData'
import { LEVEL } from '@/types/codes'
import { alarmDetail, alarmTimeline } from './mock/alertData'
import { mockAlarms } from './mock/data'
import { ackAlarm as mockAck } from './mock/scenario'
import { getActiveFires } from './emergency'

const ACTIVE = ['OPEN', 'ACKED', 'ACTING']
const notFound = () => mockFail({ code: 'ALARM_NOT_FOUND', message: '없는 알림입니다.', status: 404 })

/**
 * 지금 대응 중인 알림 (오늘, 조치 완료 전)
 * @param {{ siteId?: number, factoryId?: number }} scope 공장 담당자는 담당 범위를 넘김 (서버도 범위를 거름)
 * @returns {Promise<Array>} GET /alarms 의 items 와 같은 모양 (checklist, assignee 포함)
 */
export async function getActiveAlarms({ siteId, factoryId } = {}) {
  if (USE_MOCK) {
    const list = mockAlarms().filter(
      (a) => ACTIVE.includes(a.status) && (!siteId || a.siteId === Number(siteId)) && (!factoryId || a.factoryId === Number(factoryId)),
    )
    return mockOk(list)
  }
  const params = { status: ACTIVE.join(','), date: 'today', size: 50, ...(factoryId ? { factoryId } : siteId ? { siteId } : {}) }
  const page = await http.get('/alarms', { params })
  return markFires(page.items)
}

/**
 * 화재로 판정된 알림의 단계를 FIRE 로 바꿈 (화재 창 · 빨간 테두리 · 본사 화재 알림 띠가 이 단계를 봄)
 * - 서버 알림 단계는 NORMAL · WARN · ALARM 뿐이고, 화재는 따로 기록됨 (fire_incident, GET /fires/{id} 의 alarmId)
 * - 화재 유형(hazard FIRE) 경보가 있을 때만 화재 목록을 받아 짝을 찾음 → 평소에는 요청이 늘지 않음
 * - TODO(백엔드): 알림 줄에 fireId(또는 level FIRE)를 주면 이 함수를 뺄 수 있음 → docs/api/frontend-api-audit-1120.md 2장
 */
async function markFires(items) {
  if (!items.some((a) => a.hazard === 'FIRE' && a.level === LEVEL.ALARM)) return items
  const fires = await getActiveFires().catch(() => []) // 화재 목록을 못 받아도 알림 목록은 그대로 보여 줌
  const fireAlarmIds = new Set(fires.map((f) => f.alarmId))
  return items.map((a) => (fireAlarmIds.has(a.id) ? { ...a, level: LEVEL.FIRE } : a))
}

/** 조치 내역: { id, status, title, timeline: [{ step, at, by, text }], checklist } */
export function getAlarmTimeline(alarmId) {
  if (USE_MOCK) {
    const data = alarmTimeline(alarmId)
    return data ? mockOk(data) : notFound()
  }
  return http.get(`/alarms/${alarmId}`)
}

/** 주의 상세 */
export async function getAlarmDetail(alarmId) {
  if (USE_MOCK) {
    const data = alarmDetail(alarmId)
    return data ? mockOk(data) : notFound()
  }
  const data = await http.get(`/alarms/${alarmId}/detail`)
  // 실제 열화상 그림(VMS)이 아직 없어서 서버는 imageUrl 없이 level 만 줌 → 단계에 맞는 예시 그림 사용
  if (data.thermal && !data.thermal.imageUrl) {
    data.thermal.imageUrl = (THERMAL_BY_STATE[data.thermal.level] ?? THERMAL_BY_STATE[LEVEL.NORMAL]).image
  }
  return data
}

/**
 * 주의 확인
 * @param {number} alarmId
 * @param {string} userName 예시 데이터에서 '누가 확인했는지' 기록용 (실제 서버는 토큰으로 앎)
 */
export function ackAlarm(alarmId, userName) {
  if (USE_MOCK) {
    if (!mockAck(userName, alarmId)) return mockFail({ code: 'ALARM_ALREADY_ACKED', message: '다른 담당자가 먼저 확인했습니다.', status: 409 }) // 서버와 같은 코드 · 문구
    return mockOk({ id: Number(alarmId), status: 'ACKED' })
  }
  return http.post(`/alarms/${alarmId}/ack`)
}
