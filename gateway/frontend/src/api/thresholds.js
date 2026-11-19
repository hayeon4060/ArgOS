/**
 * 설비 기준값 관리 API (설계 자료 API 명세서 F1, 백엔드 gateway/backend .../threshold/ThresholdController)
 * - GET  /equipment/tree?factoryId=     : 공정 > 설비 목록 (왼쪽 트리)
 * - GET  /equipment/{id}/thresholds     : 센서별 계절 기준 (봄·가을 / 여름 / 겨울), 지금 계절, 판 번호
 * - POST /thresholds/simulate           : 저장하지 않고 최근 7일에 적용했을 때 알림 수
 * - PUT  /equipment/{id}/thresholds     : 저장 (version 이 달라졌으면 409)
 * - GET  /thresholds/history            : 변경 이력 (기간·센서·계절로 거름)
 * - GET  /sensors/series (최근 7일, 30분) : 변경 미리보기 그래프 (설비 상세 추이와 같은 API)
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { equipmentThresholds, equipmentTree, history, save, simulate, weekSeries } from './mock/thresholdData'

/** 공정 > 설비: [{ processId, processName, equipment: [{ id, name, type, sensorCount, level }] }] */
export function getEquipmentTree(factoryId) {
  if (USE_MOCK) return mockOk(equipmentTree())
  return http.get('/equipment/tree', { params: { factoryId } })
}

/** 센서별 계절 기준 — seasons[계절] = { warn, alarm, lowWarn, lowAlarm, warnHoldSec, alarmHoldSec, validFrom } */
export function getThresholds(equipmentId) {
  if (USE_MOCK) {
    const data = equipmentThresholds(equipmentId)
    return data ? mockOk(data) : mockFail({ code: 'EQUIPMENT_NOT_FOUND', message: '없는 설비입니다.', status: 404 })
  }
  return http.get(`/equipment/${equipmentId}/thresholds`)
}

/**
 * 변경 미리보기: { current: { warnCount, alarmCount }, proposed: {...}, diff, hits }
 * @param {{ sensorId, warn, alarm, lowWarn?, lowAlarm?, warnHoldSec?, alarmHoldSec?, days? }} body
 *   lowWarn · lowAlarm: 하한 기준 (전류 센서만 값이 있음, DB sensor_threshold.low_warn · low_alarm)
 */
export function simulateThreshold(body) {
  if (USE_MOCK) return mockOk(simulate(body))
  return http.post('/thresholds/simulate', { days: 7, ...body })
}

/**
 * 저장
 * @param {number} equipmentId
 * @param {{ season, version, reason, sensors: [{ sensorId, warn, alarm, lowWarn, lowAlarm, warnHoldSec, alarmHoldSec }] }} body
 * @param {string} by 예시 데이터에서 '바꾼 사람' 기록용 (실제 서버는 토큰으로 앎)
 */
export function saveThresholds(equipmentId, body, by) {
  if (USE_MOCK) {
    const r = save(equipmentId, body, by)
    return r.error ? mockFail(r.error) : mockOk(r.data)
  }
  return http.put(`/equipment/${equipmentId}/thresholds`, body)
}

/** 변경 이력: [{ id, sensorId, sensorTag, season, before, after, changedBy, changedAt, reason }] (최근이 위) */
export function getThresholdHistory({ equipmentId, from, to, sensorId, season }) {
  if (USE_MOCK) return mockOk(history({ equipmentId, from, to, sensorId, season }))
  return http.get('/thresholds/history', { params: { equipmentId, from, to, sensorId, season } })
}

/** 최근 7일 30분 간격 값: { sensorId, tag, unit, warn, alarm, points: [[시각, 값]] } */
export async function getSensorWeek(sensorId) {
  if (USE_MOCK) return mockOk(weekSeries(sensorId))
  const to = new Date()
  const from = new Date(to.getTime() - 7 * 24 * 3600 * 1000)
  const data = await http.get('/sensors/series', { params: { ids: sensorId, from: from.toISOString(), to: to.toISOString(), step: '30m' } })
  return data.series[0]
}
