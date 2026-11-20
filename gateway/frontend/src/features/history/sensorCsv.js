/**
 * 알림 상세 패널 '센서값 내보내기' CSV 만들기 (서버 호출 없음 — 패널에 받아 둔 시계열로 만듦)
 * - 파일 이름: 발생시각_유형_공정설비이름.csv  예) 20261119-121642_화재_도장공정_상도건조로배기팬.csv
 *   (윈도우 파일 이름에 ':' 를 쓸 수 없어 시각은 20261119-121642 모양, 빈칸은 뺌)
 * - 내용: 시각 한 줄에 센서 3종 값 (시계열), 엑셀에서 한글이 깨지지 않게 UTF-8 BOM
 */
import { HAZARD_META, SENSOR_LABEL, placeOf } from './historyFilter'
import { safeName } from '@/utils/saveFile'

/** '2026-11-19T12:16:42+09:00' → '20261119-121642' */
const stamp = (iso) => `${iso.slice(0, 10).replace(/-/g, '')}-${iso.slice(11, 19).replace(/:/g, '')}`
/** '2026-11-19T12:16:42+09:00' → '2026-11-19 12:16:42' */
const csvTime = (iso) => `${iso.slice(0, 10)} ${iso.slice(11, 19)}`

/** @param {object} d GET /stats/alarms/{id} 응답 */
export function sensorCsvName(d) {
  const place = placeOf(d.location)
  return `${stamp(d.raisedAt)}_${HAZARD_META[d.hazard]?.label ?? ''}_${safeName(place.process)}${safeName(place.equipment)}.csv`
    .replace(/_+/g, '_')
}

/** @param {object} d GET /stats/alarms/{id} 응답 → CSV Blob */
export function sensorCsvBlob(d) {
  const sensors = d.series.sensors
  // 시각을 기준으로 세 센서 값을 한 줄에 (센서마다 시각이 조금 달라도 빠지지 않게 모든 시각을 모음)
  const byTime = new Map()
  sensors.forEach((s, i) => {
    for (const [at, value] of s.points) {
      if (!byTime.has(at)) byTime.set(at, Array(sensors.length).fill(''))
      byTime.get(at)[i] = value
    }
  })
  const head = ['시각', ...sensors.map((s) => `${SENSOR_LABEL[s.type]} ${s.tag} (${s.unit})`)]
  const lines = [...byTime.keys()].sort().map((at) => [csvTime(at), ...byTime.get(at)].join(','))
  const text = `﻿${head.join(',')}\n${lines.join('\n')}\n`
  return new Blob([text], { type: 'text/csv;charset=utf-8' })
}
