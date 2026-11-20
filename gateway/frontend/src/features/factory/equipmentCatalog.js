/**
 * 설비 추가 창의 '지금 있는 설비' 목록 (2026-11-20 재원님 요청: 새 공정에 놓을 설비 목록)
 * - 지금 시스템에 있는 설비 = 울산 5공장 도장 공정 설비 (예시 데이터 processData.js EQUIPMENT,
 *   실제 중앙 서버 시드 V2__seed_ulsan_paint.sql 의 건조로 · 배기팬 · 버너실 · 펌프 · 열화상 카메라)
 * - type: DB equipment.equipment_type 코드 (layoutDraft.js EQUIPMENT_TYPES). 코드가 정해지지 않은 설비(부스 · 실링 등)는 null
 * - 같은 설비를 여러 번 추가하면 이름 뒤에 번호를 붙임 (예: 상도 건조로 2) — FactoryPage
 */
export const EQUIPMENT_CATALOG = [
  {
    group: '도장 공정 설비',
    items: [
      { name: '전처리', type: 'TANK' },
      { name: '전착 탱크', type: 'TANK' },
      { name: '전착 건조로', type: 'OVEN' },
      { name: '실링', type: null },
      { name: '중도 부스', type: null },
      { name: '중도 건조로', type: 'OVEN' },
      { name: '상도 부스', type: null },
      { name: '상도 건조로', type: 'OVEN' },
    ],
  },
  {
    group: '보조 설비',
    items: [
      { name: '배기팬', type: 'FAN' },
      { name: '버너실', type: 'BURNER' },
      { name: '펌프', type: 'PUMP' },
      { name: '열화상 카메라', type: 'CAMERA' },
      { name: '공조기 (AHU)', type: null },
      { name: '도료 혼합실', type: null },
      { name: '소각설비', type: null },
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
