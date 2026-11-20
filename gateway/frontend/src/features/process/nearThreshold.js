/**
 * 기준값 근접 센서 목록 만들기 (공정 3D 설비 배치도 오른쪽 카드)
 * - 화면용 설비 목록(processEquipment.js buildProcessEquipment 결과)의 센서를 펼쳐
 *   '지금 값 ÷ 주의 기준' 이 큰 순서로 위에서부터 보여 줌
 * - 새 API 없이 GET /processes/{id}/layout + /status 값만으로 계산
 * - 열화상(IR)처럼 주의 기준이 없는 센서, 설비에 안 붙은 예비 센서는 뺌 (눌러서 갈 설비가 없음)
 */
import { shownSensorLevel } from '@/utils/sensorLevel'

/** 카드에 보여 줄 센서 수 */
export const NEAR_LIMIT = 5

/**
 * @param {Array} equipment 화면용 설비 목록 [{ id, name, sensors: [{ id, tag, type, unit, value, level, warn, alarm }] }]
 * @param {Object} liveValues { 센서 id: 지금 값 } — 실시간 값(없으면 status 값 그대로)
 * @returns {Array} [{ id, tag, type, unit, value, warn, alarm, level, ratio, equipmentId, equipmentName }]
 */
export function nearThresholdRows(equipment, liveValues = {}, limit = NEAR_LIMIT) {
  const rows = equipment.flatMap((eq) =>
    eq.sensors
      .filter((s) => s.warn > 0 && s.value != null)
      .map((s) => {
        const value = liveValues[s.id] ?? s.value
        return {
          id: s.id,
          tag: s.tag,
          type: s.type,
          unit: s.unit,
          value,
          warn: s.warn,
          alarm: s.alarm,
          level: shownSensorLevel(s, value), // 서버 단계와 지금 값 단계 중 높은 쪽 (utils/sensorLevel.js, 2026-11-20)
          ratio: value / s.warn, // 주의 기준 대비 비율 (1 = 100%)
          equipmentId: eq.id,
          equipmentName: eq.name,
        }
      }),
  )
  return rows.sort((a, b) => b.ratio - a.ratio).slice(0, limit)
}
