/**
 * 도장 공정 3D 배치도 그림(assets/images/paint-map.svg) 안의 설비·센서 위치 정보
 * - 화면설계안 js/v-dash.js EQD, js/lib.js EQ_GROUPS 와 같은 값 (그림이 설계안 고정 그림이라 프론트에 둠)
 * - key: 그림 안 설비 이름 (API 의 설비 code 'TC-OVEN' → 'top-oven', svgKeyOf 참고)
 * - ids: 그림에서 이 설비에 속한 요소 id (건물, 배기팬, 버너 등)
 * - halo: 상태 테두리를 그릴 묶음 (첫 번째 id 기준으로 둘레를 그림)
 * - tags: 이 설비에 그려진 센서 이름표 (그림의 #tag-…) — 설비 그림을 숨길 때 같이 숨김
 * - 그림자 · 덕트 · 급기선처럼 설비 바깥에 그려진 것은 그림 파일에 data-for="설비 이름" 으로 표시해 둠 (같이 옮기고 숨김)
 * - 2026-11-20: 공장 관리에서 설비를 추가 · 삭제 (재원님 요청)
 *   · 추가한 설비는 그림을 '복사'해서 놓음 — 어떤 그림인지는 DB equipment.shape 에 그림 이름으로 둠 (drawingOf)
 *   · 등록되지 않은 설비 그림은 숨김 (hiddenDrawings)
 */

import { USE_MOCK } from '@/api/mock'

export const PAINT_EQUIPMENT = {
  pretreat: { ids: ['eq-pretreat', 'pump-pretreat'], tags: ['TE-11', 'CT-11'] },
  'ed-tank': { ids: ['eq-ed-tank', 'pump-ed-tank'], tags: ['TE-12', 'CT-12'] },
  'ed-oven': { ids: ['eq-ed-oven', 'fan-ed-oven', 'burner-ed-oven'], tags: ['CT-13', 'TE-13', 'CO-11'] },
  sealing: { ids: ['eq-sealing'], tags: [] },
  'primer-booth': { ids: ['eq-primer-booth', 'fan-primer-booth'], tags: ['CT-14'] },
  'primer-oven': { ids: ['eq-primer-oven', 'fan-primer-oven', 'burner-primer-oven'], tags: ['CT-15', 'TE-14', 'CO-12'] },
  'top-booth': { ids: ['eq-top-booth', 'fan-top-booth'], tags: ['CT-16'] },
  'top-oven': { ids: ['eq-top-oven', 'fan-top-oven', 'burner-top-oven', 'ir-top-oven'], tags: ['CT-17', 'TE-15', 'CO-13', 'IR-01'] },
  ahu: { ids: ['eq-ahu'], tags: ['CT-21'] },
  'paint-mix': { ids: ['eq-paint-mix'], tags: ['CT-22'] },
  incinerator: { ids: ['eq-incinerator'], tags: ['TE-21'] },
}

/**
 * 따로 놓을 수 있는 부품 그림 (그림에서는 건조로 · 탱크에 붙어 있는 것) — 설비 추가에서 복사해 씀
 * - 이름은 DB equipment.shape 에 들어감 (varchar(20))
 */
export const PART_SHAPES = {
  fan: { ids: ['fan-top-oven'] },
  burner: { ids: ['burner-top-oven'] },
  pump: { ids: ['pump-pretreat'] },
  ircam: { ids: ['ir-top-oven'] },
}

/** 그림 이름 → 그 그림의 요소들 { ids } (없는 이름이면 null) */
export const shapeOf = (shape) => PAINT_EQUIPMENT[shape] ?? PART_SHAPES[shape] ?? null

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

/**
 * 설비 한 대를 어떤 그림으로 그릴지 { key, shape }
 * - key: 배치도 안에서 이 설비를 가리키는 이름 (위치 · 고름 · 숨김이 모두 이 이름으로)
 * - 그림에 원래 있는 설비(code 로 짝이 맞음): key = shape = 그림 이름 ('top-oven')
 * - 공장 관리에서 추가한 설비: key = 'x' + 설비 id (저장 전에는 임시 id), shape = DB equipment.shape 에 넣어 둔 그림 이름
 *   → 그 그림을 복사해서 pos 자리에 놓음. 아는 그림이 아니면 shape = null (배치도에 안 그림)
 * @param {{ id, code?, shape? }} e GET layout 의 설비 한 줄
 */
export function drawingOf(e) {
  const own = e.code ? svgKeyOf(e.code) : ''
  if (PAINT_EQUIPMENT[own]) return { key: own, shape: own }
  return { key: `x${e.id}`, shape: shapeOf(e.shape) ? e.shape : null }
}

/**
 * 실제 서버 도장 공정(중앙 시드)에는 등록되지 않았지만 그림에는 있는 설비 — 예전처럼 그림만 보여 줌
 * (실링 · 부스 · 공조기 · 도료 혼합실 · 소각설비. backend-feedback-1120.md)
 */
const SEED_DECOR = ['sealing', 'primer-booth', 'top-booth', 'ahu', 'paint-mix', 'incinerator']

/**
 * 숨길 설비 그림: 그림에는 있지만 이 공정에 등록되지 않은 설비 (지운 설비, 새로 만든 공정)
 * - 예외: 실제 서버(예시 데이터가 아님)의 도장 공정(시드 code 가 하나라도 있음)은 SEED_DECOR 를 그대로 둠
 *   (그 그림을 공장 관리에서 추가했으면 겹치지 않게 숨김)
 *   예시 데이터는 SEED_DECOR 설비도 모두 등록돼 있어서 예외가 필요 없음 (지우면 사라져야 함)
 *   TODO(백엔드: 실링 · 부스 · 공조기 등을 시드에 넣거나 공장 관리에서 추가하면 이 예외는 지움 (docs/api/paint-equipment-edit.md)
 * - 규칙을 바꾸려면 이 함수만 고치면 됨 (대시보드 · 설비 상세 · 공장 관리가 같이 씀)
 * @param {Array<{ key, shape, code? }>} list 화면용 설비 (drawingOf 를 붙인 것)
 * @returns {string[]} 숨길 그림 이름
 */
export function hiddenDrawings(list) {
  const used = new Set(list.map((e) => e.key))
  const copied = new Set(list.map((e) => e.shape))
  const seed = !USE_MOCK && list.some((e) => REAL_CODE_KEY[e.code])
  return Object.keys(PAINT_EQUIPMENT).filter((key) => !used.has(key) && !(seed && SEED_DECOR.includes(key) && !copied.has(key)))
}

/** 그림 요소 id → 설비 이름 ('fan-top-oven' → 'top-oven') */
export function keyOfSvgId(id) {
  return Object.keys(PAINT_EQUIPMENT).find((key) => PAINT_EQUIPMENT[key].ids.includes(id)) ?? null
}
