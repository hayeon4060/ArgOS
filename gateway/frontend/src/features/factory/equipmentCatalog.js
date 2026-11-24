/**
 * 설비 추가 창의 '지금 있는 설비' 목록 (2026-11-20 재원님 요청: 새 공정에 놓을 설비 목록)
 * - 지금 시스템에 있는 설비 = 울산 5공장 도장 공정 설비 (예시 데이터 processData.js EQUIPMENT,
 *   실제 중앙 서버 시드 V2__seed_ulsan_paint.sql 의 건조로 · 배기팬 · 버너실 · 펌프 · 열화상 카메라)
 * - type: DB equipment.equipment_type 코드 (layoutDraft.js EQUIPMENT_TYPES). 코드가 정해지지 않은 설비(부스 · 실링 등)는 null
 * - shape: 배치도에 그릴 그림 이름 (features/process/paintLayout.js PAINT_EQUIPMENT · PART_SHAPES) → DB equipment.shape
 *   도장 공정 그림에 있는 설비 그림을 그대로 복사해서 놓음 (2026-11-20 재원님 요청)
 * - 같은 설비를 여러 번 추가하면 이름 뒤에 번호를 붙임 (예: 상도 건조로 2) — FactoryPage
 */
export const EQUIPMENT_CATALOG = [
  {
    group: '도장 공정 설비',
    items: [
      { name: '전처리', type: 'TANK', shape: 'pretreat' },
      { name: '전착 탱크', type: 'TANK', shape: 'ed-tank' },
      { name: '전착 건조로', type: 'OVEN', shape: 'ed-oven' },
      { name: '실링', type: null, shape: 'sealing' },
      { name: '중도 부스', type: null, shape: 'primer-booth' },
      { name: '중도 건조로', type: 'OVEN', shape: 'primer-oven' },
      { name: '상도 부스', type: null, shape: 'top-booth' },
      { name: '상도 건조로', type: 'OVEN', shape: 'top-oven' },
    ],
  },
  {
    group: '보조 설비',
    items: [
      { name: '배기팬', type: 'FAN', shape: 'fan' },
      { name: '버너실', type: 'BURNER', shape: 'burner' },
      { name: '펌프', type: 'PUMP', shape: 'pump' },
      { name: '열화상 카메라', type: 'CAMERA', shape: 'ircam' },
      { name: '공조기 (AHU)', type: null, shape: 'ahu' },
      { name: '도료 혼합실', type: null, shape: 'paint-mix' },
      { name: '소각설비', type: null, shape: 'incinerator' },
    ],
  },
]

/** 이미 있는 이름이면 뒤에 번호 (상도 건조로 → 상도 건조로 2 → 상도 건조로 3) */
export function uniqueName(name, taken) {
  if (!taken.includes(name)) return name
  let n = 2
  while (taken.includes(`${name} ${n}`)) n += 1
  return `${name} ${n}`
}
