/**
 * 도장 공정 3D 배치도: 마우스를 올린 설비 표시 (2026-11-19 재원님 시안 A 확정)
 * - 설비 묶음 단위로 표시: 건조로 3곳은 건물 + 배기팬 + 버너실(+ 상도는 열화상 카메라)을 한 덩어리로
 *   (묶음은 paintLayout.js PAINT_EQUIPMENT 의 ids 그대로)
 * - 1단계(설비 위): 설비 면을 옅은 초록으로 덮고 4px 들어 올림 + 그 설비 센서 이름표 테두리 초록
 *   · 상태 테두리 색(정상 초록 / 주의 주황 / 경보 빨강)은 그대로 둠 — 면만 덮음
 * - (2026-11-19 배기팬 2단계 표시는 대시보드 배기팬 조절 창과 함께 삭제)
 * - 그리는 일만 여기서, 언제 켜고 끌지는 PaintMap.vue 가 정함
 */
/** 설비 면 덮개 색 (시안 A: 정상 초록 #16A34A 를 옅게) */
const OVER_FILL = 'rgba(22, 163, 74, 0.2)'
const OVER_LINE = 'rgba(22, 163, 74, 0.55)'

/**
 * 초록 면 덮개 만들기 (처음 한 번)
 * - 설비 부품마다 모양을 복사해 면만 초록으로 칠하고, 원래 부품 바로 뒤(위)에 둠
 *   → 앞에 있는 다른 설비는 가리지 않음
 * - 평소에는 투명(.over), 마우스를 올리면 .hov 로 보임 (PaintMap.vue 스타일)
 * @param {SVGSVGElement} svg
 * @param {{ [key: string]: { ids: string[] } }} equipmentMap paintLayout.js PAINT_EQUIPMENT
 */
export function buildOverlays(svg, equipmentMap) {
  for (const [key, { ids }] of Object.entries(equipmentMap)) {
    for (const id of ids) {
      const el = svg.querySelector(`#${id}`)
      if (!el) continue
      const over = el.cloneNode(true)
      over.removeAttribute('id')
      over.querySelectorAll('[id]').forEach((n) => n.removeAttribute('id'))
      over.querySelectorAll('text, title').forEach((n) => n.remove())
      over.querySelectorAll('polygon, path, rect, circle, ellipse, line, polyline').forEach((n) => {
        ;['fill', 'stroke', 'filter', 'opacity', 'stroke-dasharray', 'marker-end', 'fill-opacity'].forEach((a) => n.removeAttribute(a))
        n.setAttribute('fill', OVER_FILL)
        n.setAttribute('stroke', OVER_LINE)
        n.setAttribute('stroke-width', '1')
      })
      over.setAttribute('class', 'over')
      over.setAttribute('data-eq', key)
      el.after(over)
    }
  }
}

/** 표시 지우기: 들어 올림 클래스 */
export function clearHover(svg) {
  svg.querySelectorAll('.hov').forEach((n) => n.classList.remove('hov'))
}
