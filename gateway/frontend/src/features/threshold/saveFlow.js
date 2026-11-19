/**
 * 기준값 저장 (PUT /equipment/{id}/thresholds)
 * - 이 설비만: 지금 설비 한 번
 * - 건조로 3대 모두: 같은 공정의 건조로마다 그 설비 기준을 받아서(판 번호 포함) 같은 종류 센서에
 *   '바꾼 칸만' 같은 값을 넣어 저장 (안 바꾼 칸은 그 설비에 저장된 값 그대로)
 *   (명세서·백엔드에 '여러 설비 한 번에' API 가 없어 설비마다 부름 — backend-notes 11장)
 * - 지속 조건(초)과 하한 기준(lowWarn · lowAlarm, 전류 센서)은 그 계절 저장된 값을 그대로 보냄
 *   (화면에 하한 입력 칸이 없어도, 빼고 보내면 서버에 저장된 하한이 지워짐 — DB sensor_threshold.low_warn · low_alarm)
 * - 일부 설비만 실패하면 실패한 설비 id 와 사유를 돌려줌 (화면이 '어느 설비가 저장되지 않았는지' 알림)
 */
import { getThresholds, saveThresholds } from '@/api/thresholds'
import { num, savedOf } from './thresholdForm'

/** 설비 기준 + 종류별 바꾼 값 → PUT 요청 본문 */
function bodyOf(thresholds, changedByType, season, reason) {
  return {
    season,
    version: thresholds.version,
    reason,
    sensors: thresholds.sensors.map((s) => {
      // 그 설비에 저장된 값 위에 바꾼 칸만 덮어씀
      const v = { ...savedOf(s, season), ...changedByType[s.type] }
      const now = s.seasons[season]
      return {
        sensorId: s.sensorId,
        warn: v.warn,
        alarm: v.alarm,
        lowWarn: now.lowWarn ?? null,
        lowAlarm: now.lowAlarm ?? null,
        warnHoldSec: now.warnHoldSec,
        alarmHoldSec: now.alarmHoldSec,
      }
    }),
  }
}

/**
 * @param {object} p
 * @param {object} p.thresholds 지금 설비의 GET /equipment/{id}/thresholds
 * @param {object} p.drafts     입력 칸 { 센서 id: { warn, alarm } }
 * @param {string} p.season
 * @param {number[]} p.equipmentIds 저장할 설비 (이 설비만이면 [지금 설비])
 * @param {string} p.reason
 * @param {string} p.by 예시 데이터 기록용 이름
 * @returns {Promise<{ saved: number[], failed: { id: number, message: string }[] }>}
 */
export async function saveAll({ thresholds, drafts, season, equipmentIds, reason, by }) {
  // 종류별(전류·온도·CO) 바꾼 칸만 모음 — 예: { TEMP: { warn: 158 } }
  const changedByType = {}
  for (const s of thresholds.sensors) {
    const saved = savedOf(s, season)
    for (const field of ['warn', 'alarm']) {
      const v = num(drafts[s.sensorId][field])
      if (v !== saved[field]) changedByType[s.type] = { ...changedByType[s.type], [field]: v }
    }
  }
  // 설비마다 따로 저장 — 하나가 실패해도 나머지는 계속하고, 실패한 설비를 모아서 돌려줌
  const saved = []
  const failed = []
  for (const id of equipmentIds) {
    try {
      const t = id === thresholds.equipmentId ? thresholds : await getThresholds(id)
      if (!t.sensors.length) continue
      await saveThresholds(id, bodyOf(t, changedByType, season, reason), by)
      saved.push(id)
    } catch (error) {
      failed.push({ id, message: error.message })
    }
  }
  return { saved, failed }
}
