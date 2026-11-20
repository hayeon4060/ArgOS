/**
 * 설비 상세 API (설계 자료 API 명세서 B4 '설비 상세' 기준)
 * - GET /equipment/{equipmentId}         : 설비 정보 · 센서 · 기준값 · 진행 중 체크리스트
 * - GET /equipment/{equipmentId}/events  : 오늘 이벤트 이력 (알람 · 조치 · 기준값 조정)
 * - GET /thermal/{cameraId}/latest       : 열화상 최신 장면 (최고·최저 온도)
 * - GET /thermal/images/{fileId}         : VMS 열화상 사진 한 장 (로그인 필요 → blob 으로 받음)
 * - GET /sensors/series                  : 센서 추이 (전날 겹쳐 보기 compare=yesterday)
 * - PATCH /equipment/{equipmentId}/settings : 설비 설정 바로 저장 (사용 상태 · 배기팬 자동/수동, 2026-11-19 추가)
 * - POST /fan-commands/{commandId}/approve · deny : 수동 배기팬 승인 · 거절 (가스 알림의 fanAuto.status = AWAITING, 2026-11-20 추가)
 */
import http from './http'
import { USE_MOCK, mockFail, mockOk } from './mock'
import { serverNow } from './clock'
import { THERMAL_BY_STATE, TOP_OVEN_ID, mockEquipment, mockEquipmentEvents, thermalOf } from './mock/equipmentData'
import { buildSeries } from './mock/equipmentSeries'
import { saveSettings } from './mock/layoutData'
import { decideFanCommand, fanAutoMode } from './mock/fanAuto'
import { emitLive } from './live'
import { scenario } from './mock/scenario'
import { LEVEL } from '@/types/codes'

const notFound = () => mockFail({ code: 'EQUIPMENT_NOT_FOUND', message: '없는 설비입니다.', status: 404 })

/** 설비 정보: { id, code, name, processId, seq, path, level, runState, sensors: [...], cameraId, activeChecklist } */
export function getEquipment(equipmentId) {
  if (USE_MOCK) {
    const data = mockEquipment(equipmentId) // 지금 예시 상황으로 만듦
    return data ? mockOk(data) : notFound()
  }
  return http.get(`/equipment/${equipmentId}`)
}

/** 오늘 이벤트 이력: [{ at, type, level, text, detail, by, status, alarmId }] (최근이 위) */
export function getEquipmentEvents(equipmentId) {
  if (USE_MOCK) return mockOk(mockEquipmentEvents(equipmentId))
  return http.get(`/equipment/${equipmentId}/events`, { params: { date: 'today' } })
}

/**
 * 열화상 최신 장면: { imageUrl, capturedAt, max, min, avg, maxPoint, scale, zone }
 * - equipmentId: 이 설비 구역의 최고·최저 온도를 받기 위해 붙임 (명세서에 없음, backend-notes 참고)
 */
export async function getThermalLatest(cameraId, equipmentId) {
  if (USE_MOCK) return mockOk(thermalOf(cameraId, equipmentId))
  const data = await http.get(`/thermal/${cameraId}/latest`, { params: { equipmentId } })
  // 실제 열화상 그림(VMS)이 아직 없어서 서버는 imageUrl 없이 level 만 줌 → 단계에 맞는 예시 그림 사용
  if (!data.imageUrl) data.imageUrl = (THERMAL_BY_STATE[data.level] ?? THERMAL_BY_STATE[LEVEL.NORMAL]).image
  return data
}

/**
 * 열화상 사진을 화면 <img> 에 넣을 주소로 바꿈
 * - 서버가 주는 thermal.imageUrl 은 '/api/v1/thermal/images/{fileId}' (ThermalService.imageUrl)
 *   → 로그인 토큰이 필요해서 <img src> 로 바로 못 띄움. axios(토큰 헤더)로 받아 브라우저 안 주소(blob:)로 만듦
 * - 그 밖의 주소(예시 그림 · 빈 값)는 받을 필요 없이 그대로 씀 (mock 모드는 늘 이쪽)
 * - 다 쓰면 revoke() 로 blob 주소를 풀어 줌 (getInspectionFile 과 같은 방식)
 * @param {string|null} imageUrl
 * @returns {Promise<{ url: string|null, revoke: () => void }>}
 */
export async function loadThermalImage(imageUrl) {
  const path = imageUrl?.match(/\/thermal\/images\/\d+$/)?.[0] // '/thermal/images/12' (앞의 /api/v1 은 http 기본 주소가 붙임)
  if (!path) return { url: imageUrl ?? null, revoke: () => {} }
  const blob = await http.get(path, { responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  return { url, revoke: () => URL.revokeObjectURL(url) }
}

/** 범위 → 요청 기간·간격 ('1h' = 최근 1시간 1분 간격, '6h' = 최근 6시간, 'today' = 오늘 0시부터) */
function periodOf(range) {
  const to = serverNow() // 시연 시계 기준 (PC 시계면 서버 데이터와 날짜가 어긋남)
  const from = new Date(to)
  if (range === '1h') from.setHours(to.getHours() - 1)
  else if (range === '6h') from.setHours(to.getHours() - 6)
  else from.setHours(0, 0, 0, 0)
  return { from: from.toISOString(), to: to.toISOString(), step: range === '1h' ? '1m' : '10m' }
}

/**
 * 센서 추이
 * @param {{ equipment: object, range: '1h'|'6h'|'today' }} params equipment 는 getEquipment 응답
 * @returns {Promise<{ step, series: [{ sensorId, tag, unit, warn, alarm, points: [[시각, 값]], compare: [[시각, 값]] }] }>}
 * - 전날 값(compare)은 늘 같이 받고, '전날 같이 보기' 스위치는 화면에서 보이기만 바꿈
 */
export function getSensorSeries({ equipment, range }) {
  if (USE_MOCK) {
    // 상도 건조로가 주의일 때만 16:30 부터 값이 오르는 그래프, 그 밖에는 평소 값 근처
    const rising = equipment.id === TOP_OVEN_ID && scenario.state === LEVEL.WARN
    return mockOk(buildSeries(equipment.sensors, rising, range))
  }
  const ids = equipment.sensors.map((s) => s.id).join(',')
  return http.get('/sensors/series', { params: { ids, ...periodOf(range), compare: 'yesterday' } })
}

/**
 * 설비 설정 바로 저장 (설비 기준값 관리 창 위쪽 스위치 2개)
 * @param {number} equipmentId
 * @param {{ active?: boolean, fanAuto?: boolean }} body 바꾼 값만
 * @returns {Promise<{ equipmentId, active, fanAuto }>}
 * - 실제 서버는 저장 뒤 SSE equipment.updated 를 보냄 → 열린 화면이 다시 불러옴. 예시 데이터는 여기서 같은 신호
 */
export async function updateEquipmentSettings(equipmentId, body) {
  if (USE_MOCK) {
    const data = await mockOk({ ...saveSettings(equipmentId, body), fanAuto: fanAutoMode(equipmentId) })
    emitLive('equipment.updated')
    return data
  }
  return http.patch(`/equipment/${equipmentId}/settings`, body)
}

/**
 * 수동 배기팬 승인 · 거절 (가스 알림 창의 '승인' · '거절' 버튼)
 * - 공장 담당자만 (본사 관리자 403). 이미 승인 · 거절 · 취소된 명령이면 409 COMMAND_NOT_AWAITING
 * - 실제 서버는 처리 뒤 SSE command.result · alarm.updated 를 보냄 → 알림 목록 · 창이 새 fanAuto 로 바뀜
 * @param {number} commandId 알림의 fanAuto.commandId
 * @param {boolean} approve true 승인 / false 거절
 * @returns {Promise<{ commandId, equipmentId, status: 'SENT'|'DENIED', hz, rpm }>}
 */
export function decideFan(commandId, approve) {
  if (USE_MOCK) {
    const r = decideFanCommand(commandId, approve)
    return r.error ? mockFail(r.error) : mockOk(r.data)
  }
  return http.post(`/fan-commands/${commandId}/${approve ? 'approve' : 'deny'}`)
}
