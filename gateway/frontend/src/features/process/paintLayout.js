/**
 * 도장 공정 3D 배치도 그림(assets/images/paint-map.svg) 안의 설비·센서 위치 정보
 * - 화면설계안 js/v-dash.js EQD, js/lib.js EQ_GROUPS 와 같은 값 (그림이 설계안 고정 그림이라 프론트에 둠)
 * - key: 그림 안 설비 이름 (API 의 설비 code 'TC-OVEN' → 'top-oven', svgKeyOf 참고)
 * - ids: 그림에서 이 설비에 속한 요소 id (건물, 배기팬, 버너 등)
 * - halo: 상태 테두리를 그릴 묶음 (첫 번째 id 기준으로 둘레를 그림)
 */

export const PAINT_EQUIPMENT = {
  pretreat: { ids: ['eq-pretreat', 'pump-pretreat'] },
  'ed-tank': { ids: ['eq-ed-tank', 'pump-ed-tank'] },
  'ed-oven': { ids: ['eq-ed-oven', 'fan-ed-oven', 'burner-ed-oven'] },
  sealing: { ids: ['eq-sealing'] },
  'primer-booth': { ids: ['eq-primer-booth', 'fan-primer-booth'] },
  'primer-oven': { ids: ['eq-primer-oven', 'fan-primer-oven', 'burner-primer-oven'] },
  'top-booth': { ids: ['eq-top-booth', 'fan-top-booth'] },
  'top-oven': { ids: ['eq-top-oven', 'fan-top-oven', 'burner-top-oven', 'ir-top-oven'] },
  ahu: { ids: ['eq-ahu'] },
  'paint-mix': { ids: ['eq-paint-mix'] },
  incinerator: { ids: ['eq-incinerator'] },
}

/**
 * 상태 테두리 묶음 (설계안 EQ_GROUPS)
 * - 버너는 건물과 따로 테두리를 그림 (버너만 경보일 수 있어서)
 */
export const HALO_GROUPS = [
  ['eq-pretreat', 'pump-pretreat'],
  ['eq-ed-tank', 'pump-ed-tank'],
  ['eq-ed-oven', 'fan-ed-oven'],
  ['burner-ed-oven'],
  ['eq-sealing'],
  ['eq-primer-booth', 'fan-primer-booth'],
  ['eq-primer-oven', 'fan-primer-oven'],
  ['burner-primer-oven'],
  ['eq-top-booth', 'fan-top-booth'],
  ['eq-top-oven', 'fan-top-oven'],
  ['burner-top-oven'],
  ['eq-ahu'],
  ['eq-paint-mix'],
  ['eq-incinerator'],
]

/**
 * 실제 서버 설비 code → 그림 안 설비 이름 (중앙 시드 V2__seed_ulsan_paint.sql, 공정 12 · 2026-11-20 백엔드 피드백)
 * - 실제 서버는 건조로 · 배기팬 · 버너실 · 펌프 · 열화상 카메라가 각각 따로 설비라, 같은 그림 하나에 여러 대가 붙음
 *   → 그림에서는 대표 설비(seq 가 있는 탱크 · 건조로) 하나로 묶어 보여 줌 (processEquipment.js mergeByKey)
 * - 실링 · 중도/상도 부스 · 공조기 · 도료 혼합실 · 소각설비는 실제 시드에 없음 → 예시 데이터만 'PNT-' 코드로 남김
 */
const REAL_CODE_KEY = {
  'PT-TANK': 'pretreat',
  'PT-PUMP': 'pretreat',
  'ED-TANK': 'ed-tank',
  'ED-PUMP': 'ed-tank',
  'ED-OVEN': 'ed-oven',
  'ED-FAN': 'ed-oven',
  'ED-BURNER': 'ed-oven',
  'PR-OVEN': 'primer-oven',
  'PR-FAN': 'primer-oven',
  'PR-BURNER': 'primer-oven',
  'TC-OVEN': 'top-oven',
  'TC-FAN': 'top-oven',
  'TC-BURNER': 'top-oven',
  'IR-CAM-01': 'top-oven',
}

/** 설비 code → 그림 안 설비 이름 ('TC-OVEN' → 'top-oven'). 표에 없으면 예전 규칙 ('PNT-TOP-BOOTH' → 'top-booth') */
export function svgKeyOf(code) {
  return REAL_CODE_KEY[code] ?? code.replace(/^PNT-/, '').toLowerCase()
}

/** 그림 요소 id → 설비 이름 ('fan-top-oven' → 'top-oven') */
export function keyOfSvgId(id) {
  return Object.keys(PAINT_EQUIPMENT).find((key) => PAINT_EQUIPMENT[key].ids.includes(id)) ?? null
}
