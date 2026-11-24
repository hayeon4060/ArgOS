/**
 * 경보 창 '센서 데이터'에 보여 줄 센서 고르기 (2026-11-20)
 *
 * 문제: 상도 건조로 경보 창에 TE-301 ~ TE-305, IT-301 … 처럼 도장 공정 센서가 전부 나왔음
 *  - 서버 GET /alarms/{id}/response 의 sensors 가 '같은 공정의 센서 전부' (AlarmService.response)
 *  - 설계안 mAlarm() 은 경보가 난 관제 단위의 센서 3종(전류 · 온도 · CO)만 보여 줌
 *  - 관제 단위(상도 건조로)의 센서는 여러 설비에 나뉘어 달려 있음
 *    (TE-305 = 건조로 본체, IT-307 = 배기팬, AT-303 = 버너실, IR-301 = 열화상 카메라)
 *    → 설비 이름으로는 못 거르고, 관제 단위 구성 센서로 걸러야 함
 *
 * 방법: GET /alarms/{id}/detail 의 sensors 는 서버가 이미 관제 단위 센서로 걸러서 줌
 *  → 그 태그만 남김 (값 · 단계는 자주 다시 받는 response 쪽 값을 그대로 씀)
 *  - 서버가 response 도 단위 센서만 주게 바뀌면(docs/api/alarm-sensor-scope.md) 걸러도 그대로라 문제없음
 *  - detail 을 못 받으면(예시 데이터, 단위 없는 경보) 받은 목록에서 열화상만 빼고 보여 줌 (예전과 같음)
 *
 * 단계: 설비 상세와 같은 규칙 — 서버 단계와 '지금 값 vs 위쪽 기준' 중 높은 쪽 (utils/sensorLevel.js, PR #91)
 * 순서: 설계안처럼 전류 → 온도 → CO (서버 추이 순서 trendOrder 와 같음)
 */
import { SENSOR_TYPES } from '@/types/codes'
import { shownSensorLevel } from '@/utils/sensorLevel'

// 게이지로 보여 주는 종류 = 전류 · 온도 · CO (열화상 IR 은 왼쪽 열화상 카메라로 따로 보여 줌)
const ORDER = SENSOR_TYPES.map((t) => t.type) // ['CURRENT', 'TEMP', 'CO']

/**
 * detail 응답에서 관제 단위 센서 태그 모음 꺼내기
 * @param {object|null} detail GET /alarms/{id}/detail 응답
 * @returns {Set<string>|null} 태그 모음 (못 구하면 null = 거르지 않음)
 */
export function unitTagsOf(detail) {
  const tags = (detail?.sensors ?? []).map((s) => s.tag).filter(Boolean)
  return tags.length ? new Set(tags) : null
}

/**
 * 경보 창에 그릴 센서 목록
 * @param {Array} sensors GET /alarms/{id}/response 의 sensors
 * @param {Set<string>|null} unitTags unitTagsOf() 결과 (null 이면 단위로 거르지 않음)
 */
export function pickAlarmSensors(sensors, unitTags) {
  return (sensors ?? [])
    .filter((s) => ORDER.includes(s.type)) // 전류 · 온도 · CO 만
    .filter((s) => !unitTags || unitTags.has(s.tag)) // 경보가 난 관제 단위 센서만
    .sort((a, b) => ORDER.indexOf(a.type) - ORDER.indexOf(b.type))
    .map((s) => ({ ...s, level: shownSensorLevel(s) }))
}
