/**
 * 기준값 입력 다루기 (화면설계안 vThreshold() 의 num · fmt · check · dirtyList)
 * - 입력 칸 값은 글자(string)로 들고 있다가 숫자로 바꿔 비교
 * - 검사 순서와 문구는 설계안 그대로
 */

/** 계절 이름·기간 (설계안 SEA) */
export const SEASON_META = {
  SPRING_FALL: { label: '봄 · 가을', months: '3~5월, 9~10월' },
  SUMMER: { label: '여름', months: '6~8월' },
  WINTER: { label: '겨울', months: '11~2월' },
}

/** '21.6' → 21.6, 숫자가 아니면 null */
export function num(text) {
  const t = String(text ?? '').trim()
  return /^\d+(\.\d+)?$/.test(t) ? Number(t) : null
}

/** 21.60 → '21.6', 27 → '27' */
export const fmt = (v) => String(Number(Number(v).toFixed(1)))

/** 센서의 그 계절 저장된 기준 { warn, alarm } */
export const savedOf = (sensor, season) => sensor.seasons[season]

/**
 * 한 센서 입력 검사 → null(문제 없음) 또는 { level: 'err' | 'warn', text }
 * - err 가 하나라도 있으면 저장 단추가 꺼짐, warn 은 안내만
 */
export function check(sensor, draft) {
  const w = num(draft.warn)
  const a = num(draft.alarm)
  if (w === null || a === null || w <= 0 || a <= 0) return { level: 'err', text: '숫자로 입력해 주세요.' }
  if (w >= a) return { level: 'err', text: '주의 기준은 경보 기준보다 낮아야 합니다.' }
  if (sensor.legal && a > sensor.legal.max) return { level: 'err', text: `${label(sensor)} 경보 기준은 ${sensor.legal.max} ${sensor.unit}보다 높게 정할 수 없습니다.` }
  if (sensor.max && a > sensor.max) return { level: 'err', text: `경보 기준은 ${sensor.max} ${sensor.unit} 이하로 입력해 주세요.` }
  if (sensor.setpoint != null && w <= sensor.setpoint) {
    return { level: 'warn', text: `주의 기준이 평소값(${sensor.setpoint} ${sensor.unit})보다 낮아 알림이 계속 울릴 수 있습니다.` }
  }
  return null
}

const TYPE_LABEL = { CURRENT: '전류', TEMP: '온도', CO: 'CO' }
export const label = (sensor) => TYPE_LABEL[sensor.type] ?? sensor.type

/** 바꾼 값 목록 [{ sensor, field: 'warn'|'alarm', before, after }] (after 가 숫자가 아니면 null) */
export function changes(sensors, drafts, season) {
  const list = []
  for (const s of sensors) {
    const d = drafts[s.sensorId]
    if (!d) continue
    for (const field of ['warn', 'alarm']) {
      const before = savedOf(s, season)[field]
      const after = num(d[field])
      if (after === null || after !== before) list.push({ sensor: s, field, before, after })
    }
  }
  return list
}

/** 입력 칸 처음 값 (저장된 값) */
export function draftsOf(sensors, season) {
  return Object.fromEntries(sensors.map((s) => [s.sensorId, { warn: fmt(savedOf(s, season).warn), alarm: fmt(savedOf(s, season).alarm) }]))
}
