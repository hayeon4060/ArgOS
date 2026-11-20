<!--
  도장 공정 3D 배치도 (축측 그림 + 설비 상태 테두리)
  - 그림: 화면설계안 assets/svgs.js 의 도장 공정 배치도를 정적 SVG 로 만든 것 (assets/images/paint-map.svg)
    3D 엔진(Three.js 등)을 쓰지 않고 설계안처럼 비스듬히 그린 그림 위에 표시만 함
  - 설비 상태: 설비 둘레에 상태 색 테두리 (면은 칠하지 않음). 주의·경보 설비만 테두리 빛번짐
  - 센서 이름표: 주의·경보 센서는 이름표 테두리를 상태 색으로
  - 마우스를 올리면 설비 묶음 전체 표시 + 이름 칩(PaintHoverChip), 누르면 select(설비) — 배기팬을 눌러도 그 설비 선택
    · 2026-11-19 재원님 요청: 대시보드에서 배기팬을 눌러 회전수·켜기/끄기를 바꾸던 창(FanPopover)과 배기팬 2단계 표시를 모두 삭제
    · 2026-11-19 재원님 시안 A: 설비 면 옅은 초록 + 4px 들어 올림 (건조로는 건물·배기팬·버너실을 한 덩어리로)
    · 그리는 방법은 paintHover.js
  - 설비 위치 positions { 설비 이름: { x, y } }: 배치도 화면 좌표 = DB equipment.pos_x · pos_y (API layout 의 pos)
    · 이 그림의 좌표계(SVG viewBox 단위)에서 설비 가운데 점. 값이 없는 설비는 그림에 그려진 자리 그대로
    · 대시보드 · 설비 상세 · 공장 관리가 같은 값으로 그림
  - 공장 관리(editable): 설비를 끌어서 옮김 → drag(설비 이름, { x, y }) / 끝나면 dragend
    · 격자에 맞추기(snap)를 켜면 격자 한 칸씩 (격자 방향은 설계안 공장 관리 GU · GV, 그림 좌표)
    · selected: 고른 설비 파란 그림자, inactive: 사용 안 함 설비 흐리게, thin: 테두리를 더 얇게
-->
<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import PaintHoverChip from './PaintHoverChip.vue'
import { buildOverlays, clearHover } from './paintHover'
import { HALO_GROUPS, PAINT_EQUIPMENT, keyOfSvgId } from './paintLayout'
import { mergeByKey } from './processEquipment'
import paintSvg from '@/assets/images/paint-map.svg?raw'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  /** 화면용 설비 목록 [{ key, name, level, fan, sensors: [{ tag, type, value, unit, level }] }] (ProcessPage 에서 만듦) */
  equipment: { type: Array, required: true },
  /** 이 설비만 크게 보기 (설비 상세 '설비 위치'). 그림 안 설비 이름 (예: 'top-oven'), 비우면 전체 */
  focus: { type: String, default: '' },
  /** 설비별 배치도 위치 { 'top-oven': { x: 412.5, y: 380 } } — 화면 좌표(DB pos_x · pos_y). 없는 설비는 그림 그대로 */
  positions: { type: Object, default: () => ({}) },
  /** 끌어서 옮기기 (공장 관리) */
  editable: { type: Boolean, default: false },
  /** 격자에 맞추기 (끌 때 칸 단위로) */
  snap: { type: Boolean, default: true },
  /** 고른 설비 이름 */
  selected: { type: String, default: '' },
  /** 사용 안 함 설비 이름들 (흐리게) */
  inactive: { type: Array, default: () => [] },
  /** 테두리를 더 얇게 (공장 관리) */
  thin: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'drag', 'dragend'])

// 격자 한 칸 방향 (그림 좌표, 설계안 v-pages2.js GU · GV)
const GU = [62.43, 19.57]
const GV = [-18.4, 24.25]
const gridXY = (p) => [p.a * GU[0] + p.b * GV[0], p.a * GU[1] + p.b * GV[1]]
/** 그림 좌표 이동(dx, dy) → 격자 칸(a, b) */
function solve(dx, dy) {
  const det = GU[0] * GV[1] - GV[0] * GU[1]
  return [(dx * GV[1] - GV[0] * dy) / det, (GU[0] * dy - dx * GU[1]) / det]
}

const box = ref(null)

// 상태 → 테두리 색 / 그림의 data-level 값
const COLOR = {
  [LEVEL.NORMAL]: '#16A34A',
  [LEVEL.WARN]: '#F97316',
  [LEVEL.ALARM]: '#DC2626',
  [LEVEL.FIRE]: '#DC2626',
}
const TAG_LEVEL = { [LEVEL.NORMAL]: 'normal', [LEVEL.WARN]: 'warn', [LEVEL.ALARM]: 'danger', [LEVEL.FIRE]: 'danger' }

// 그림 이름별 설비 (실제 서버는 건조로 · 배기팬 · 버너실이 따로 설비 → 대표 설비 하나로 묶음, processEquipment.js)
const merged = computed(() => mergeByKey(props.equipment))
const byKey = (key) => merged.value.find((e) => e.key === key)

/**
 * 상태 테두리 만들기 (처음 한 번)
 * - 설비 모양을 복사해서 원래 그림 뒤에 두껍게 깔면 둘레만 테두리처럼 보임 (설계안 paintMap 과 같은 방식)
 * - 복사본은 면을 칠하지 않고 선만 그림 → 색은 나중에 applyLevels 에서 바꿈
 */
function buildHalos(svg) {
  for (const ids of HALO_GROUPS) {
    const first = svg.querySelector(`#${ids[0]}`)
    if (!first) continue
    const halo = document.createElementNS('http://www.w3.org/2000/svg', 'g')
    halo.setAttribute('class', 'halo')
    halo.setAttribute('data-halo', ids[0])
    for (const id of ids) {
      const el = svg.querySelector(`#${id}`)
      if (!el || (id !== ids[0] && first.contains(el))) continue
      const copy = el.cloneNode(true)
      copy.removeAttribute('id')
      copy.querySelectorAll('[id]').forEach((n) => n.removeAttribute('id'))
      copy.querySelectorAll('text, title').forEach((n) => n.remove())
      copy.querySelectorAll('polygon, path, rect, circle, ellipse, line, polyline').forEach((n) => {
        const width = parseFloat(n.getAttribute('stroke-width') || 1)
        ;['fill', 'stroke', 'filter', 'opacity', 'stroke-dasharray', 'marker-end'].forEach((a) => n.removeAttribute(a))
        n.setAttribute('fill', 'none') // 면 채우기 금지
        n.setAttribute('stroke-linejoin', 'round')
        n.setAttribute('stroke-linecap', 'round')
        n.style.strokeWidth = `calc(${width}px + var(--halo-width))`
      })
      halo.appendChild(copy)
    }
    first.parentNode.insertBefore(halo, first)
  }
}

/** 설비를 누를 수 있게 표시 (data-eq) */
function markEquipment(svg) {
  for (const [key, { ids }] of Object.entries(PAINT_EQUIPMENT)) {
    ids.forEach((id) => {
      const el = svg.querySelector(`#${id}`)
      if (!el) return
      el.classList.add('eqh')
      el.setAttribute('data-eq', key)
    })
  }
}

/** 설비 상태·센서 상태를 그림에 반영 */
function applyLevels() {
  const svg = box.value?.querySelector('svg')
  if (!svg) return
  svg.querySelectorAll('.halo').forEach((halo) => {
    const svgId = halo.getAttribute('data-halo')
    const eq = byKey(keyOfSvgId(svgId))
    // 버너실은 설비 전체가 아니라 CO 센서 단계로 색을 정함 (전류만 주의면 버너는 정상)
    const level = svgId.startsWith('burner-')
      ? (eq?.sensors.find((s) => s.type === 'CO')?.level ?? LEVEL.NORMAL)
      : (eq?.level ?? LEVEL.NORMAL)
    const calm = level === LEVEL.NORMAL
    halo.style.setProperty('--halo-width', props.thin ? (calm ? '3px' : '4px') : calm ? '5px' : '8px') // 정상은 얇게
    halo.style.stroke = COLOR[level]
    halo.style.color = COLOR[level] // 빛번짐(drop-shadow)이 이 색을 씀
    halo.classList.toggle('glow', !calm) // 빛번짐은 주의·경보 테두리에만
    halo.classList.toggle('fast', level === LEVEL.ALARM || level === LEVEL.FIRE) // 경보·화재는 더 빠르게 깜빡임
  })
  for (const eq of merged.value) {
    // 그림 안 설비 이름표 (공장 관리에서 이름을 바꾸면 그 이름으로) — 번호(숫자)가 아닌 글자
    const label = [...(svg.querySelector(`#eq-${eq.key}`)?.querySelectorAll('text') ?? [])].find((t) => !/^\d+$/.test(t.textContent))
    if (label && eq.name && label.textContent !== eq.name) label.textContent = eq.name
    for (const s of eq.sensors) {
      svg.querySelector(`#tag-${s.tag}`)?.setAttribute('data-level', TAG_LEVEL[s.level] ?? 'normal')
    }
  }
}

/**
 * focus 설비만 크게: 그 설비 그림 + 센서 이름표를 감싸는 사각형으로 viewBox 를 좁힘
 * - 여백은 설계안 vEquip() 과 같은 식 (가로 420 · 세로 285 보다 작으면 가운데에 두고 양옆을 채움)
 */
function zoomToFocus(svg) {
  if (!props.focus) return
  const eq = byKey(props.focus)
  const selectors = [
    ...(PAINT_EQUIPMENT[props.focus]?.ids ?? []).map((id) => `#${id}`),
    ...(eq?.sensors ?? []).map((s) => `#tag-${s.tag}`),
  ]
  let box2 = null
  for (const sel of selectors) {
    const el = svg.querySelector(sel)
    if (!el) continue
    const b = el.getBBox()
    box2 = box2
      ? { x0: Math.min(box2.x0, b.x), y0: Math.min(box2.y0, b.y), x1: Math.max(box2.x1, b.x + b.width), y1: Math.max(box2.y1, b.y + b.height) }
      : { x0: b.x, y0: b.y, x1: b.x + b.width, y1: b.y + b.height }
  }
  if (!box2) return
  // 공장 관리에서 옮긴 설비면 그 자리로 (getBBox 는 CSS 이동을 모름)
  const [ox, oy] = shiftOf(props.focus)
  box2 = { x0: box2.x0 + ox, y0: box2.y0 + oy, x1: box2.x1 + ox, y1: box2.y1 + oy }
  const w = box2.x1 - box2.x0
  const h = box2.y1 - box2.y0
  const padX = Math.max(40, (420 - w) / 2)
  const padY = Math.max(24, (285 - h) / 2)
  svg.setAttribute('viewBox', `${box2.x0 - padX} ${box2.y0 - padY} ${w + padX * 2} ${h + padY * 2}`)
}

/* ───────── 위치 · 고름 · 사용 안 함 ───────── */
/** 그림에 그려진 설비 가운데 점 { 설비 이름: [x, y] } (처음 그릴 때 한 번 잼 — 위치 값이 없을 때의 자리) */
const centers = {}
function measureCenters(svg) {
  for (const key of Object.keys(PAINT_EQUIPMENT)) {
    let x0 = Infinity
    let y0 = Infinity
    let x1 = -Infinity
    let y1 = -Infinity
    for (const id of PAINT_EQUIPMENT[key].ids) {
      const el = svg.querySelector(`#${id}`)
      if (!el) continue
      const b = el.getBBox() // CSS 이동과 상관없이 그려진 자리
      x0 = Math.min(x0, b.x)
      y0 = Math.min(y0, b.y)
      x1 = Math.max(x1, b.x + b.width)
      y1 = Math.max(y1, b.y + b.height)
    }
    if (x0 !== Infinity) centers[key] = [(x0 + x1) / 2, (y0 + y1) / 2]
  }
}
/** 그려진 자리에서 얼마나 옮겨 그릴지 [dx, dy] (위치 값 - 가운데 점) */
function shiftOf(key) {
  const p = props.positions[key]
  const c = centers[key]
  return p && c && p.x != null && p.y != null ? [p.x - c[0], p.y - c[1]] : [0, 0]
}
const round1 = (v) => Math.round(v * 10) / 10
/** 설비 하나에 속한 그림 요소 (건물·팬·버너 + 초록 면 덮개 + 상태 테두리 + 센서 이름표) */
function partsOf(svg, key) {
  const ids = PAINT_EQUIPMENT[key]?.ids ?? []
  const els = ids.map((id) => svg.querySelector(`#${id}`)).filter(Boolean)
  const top = els.filter((el) => !els.some((o) => o !== el && o.contains(el)))
  const overs = [...svg.querySelectorAll(`.over[data-eq="${key}"]`)]
  const halos = [...svg.querySelectorAll('.halo')].filter((h) => keyOfSvgId(h.getAttribute('data-halo')) === key)
  return [...top, ...overs, ...halos, ...tagsOf(svg, key)]
}
/** 설비에 붙은 센서 이름표 */
const tagsOf = (svg, key) => (byKey(key)?.sensors ?? []).map((s) => svg.querySelector(`#tag-${s.tag}`)).filter(Boolean)

function applyOffsets() {
  const svg = box.value?.querySelector('svg')
  if (!svg) return
  for (const key of Object.keys(PAINT_EQUIPMENT)) {
    const [x, y] = shiftOf(key)
    const off = props.inactive.includes(key)
    for (const el of partsOf(svg, key)) {
      el.style.transform = x || y ? `translate(${x}px, ${y}px)` : ''
      el.style.opacity = off ? '0.4' : ''
      el.classList.toggle('sel', props.editable && key === props.selected)
    }
  }
}

/** 위치 값이 없을 때 쓰는 기본 위치 { 설비 이름: { x, y } } (그려진 가운데 점) — 공장 관리 위치 X · Y 표시 · 변경 계산 */
function defaultPositions() {
  return Object.fromEntries(Object.entries(centers).map(([key, [x, y]]) => [key, { x: round1(x), y: round1(y) }]))
}
defineExpose({ defaultPositions })

/* ───────── 끌어서 옮기기 (editable) ───────── */
let drag = null // { key, start: [화면 x, y], from: { x, y } 그림 좌표, scale }
function onDown(event) {
  if (!props.editable) return
  const key = event.target.closest('.eqh')?.getAttribute('data-eq')
  if (!key) return
  event.preventDefault() // 끄는 동안 글자 선택·그림 끌기(브라우저 기본 동작) 막기
  const svg = box.value.querySelector('svg')
  const m = svg.getScreenCTM()
  const c = centers[key] ?? [0, 0]
  const from = props.positions[key]?.x != null ? { ...props.positions[key] } : { x: c[0], y: c[1] }
  drag = { key, start: [event.clientX, event.clientY], from, scale: m ? m.a : 1, moved: false }
  box.value.setPointerCapture(event.pointerId)
  onLeave() // 끄는 동안 칩 숨김
  emit('select', byKey(key) ?? { key })
}
function onDrag(event) {
  if (!drag) return
  const dx = (event.clientX - drag.start[0]) / drag.scale
  const dy = (event.clientY - drag.start[1]) / drag.scale
  // 격자에 맞추기: 끈 거리를 격자 칸으로 바꿔 반올림한 뒤 다시 그림 좌표로 (켜져 있지 않으면 그대로)
  const [a, b] = solve(dx, dy)
  const [ox, oy] = props.snap ? gridXY({ a: Math.round(a), b: Math.round(b) }) : [dx, dy]
  const next = { x: round1(drag.from.x + ox), y: round1(drag.from.y + oy) }
  if (Math.abs(dx) + Math.abs(dy) > 3) drag.moved = true
  emit('drag', drag.key, next)
}
function onUp() {
  if (!drag) return
  if (drag.moved) emit('dragend', drag.key)
  drag = null
}

onMounted(() => {
  const svg = box.value.querySelector('svg')
  buildHalos(svg)
  markEquipment(svg)
  buildOverlays(svg, PAINT_EQUIPMENT) // 마우스 올림용 초록 면 덮개 (paintHover.js)
  measureCenters(svg) // 옮기기 전에 그려진 자리를 잼
  applyLevels()
  applyOffsets()
  zoomToFocus(svg)
})
watch(() => props.equipment, () => nextTick(applyLevels), { deep: true })
watch(() => [props.positions, props.selected, props.inactive], () => nextTick(applyOffsets), { deep: true })

/* ───────── 마우스 올림: 설비 묶음 표시 + 이름 칩 ───────── */
// { key: 설비 이름 } — 아무것도 아니면 null
const hover = ref(null)
// 이름 칩 자리 (그림 상자 안 좌표, 설비 위쪽 가운데)
const chipAt = ref({ x: 0, y: 0 })

/** 마우스 아래 설비 (설비 그림 또는 그 설비의 센서 이름표) */
function hoverOf(target) {
  const eqEl = target.closest('.eqh')
  if (eqEl) {
    return { key: eqEl.getAttribute('data-eq') }
  }
  const tag = target.closest('[id^="tag-"]')?.id.slice(4)
  const eq = tag && merged.value.find((e) => e.sensors.some((s) => s.tag === tag))
  return eq ? { key: eq.key } : null
}

function onMove(event) {
  if (drag) return
  const next = hoverOf(event.target)
  const now = hover.value
  if (next?.key === now?.key) return // 같은 설비면 그대로
  hover.value = next && byKey(next.key) ? next : null
  applyHover()
}

function onLeave() {
  hover.value = null
  applyHover()
}

/** 그림에 표시 반영 (공장 관리 editable 에서는 칩만, 들어 올림·초록 면은 안 함 — 끌어 옮기기와 헷갈리지 않게) */
function applyHover() {
  const svg = box.value?.querySelector('svg')
  if (!svg) return
  clearHover(svg)
  const h = hover.value
  if (!h) return
  if (!props.editable) {
    partsOf(svg, h.key).forEach((el) => el.classList.add('hov'))
  }
  placeChip(svg, h)
}

/** 칩 자리: 설비 묶음 위쪽 가운데 (마우스를 따라다니지 않아 흔들리지 않음) */
function placeChip(svg, h) {
  const els = (PAINT_EQUIPMENT[h.key]?.ids ?? []).map((id) => svg.querySelector(`#${id}`))
  const rects = els.filter(Boolean).map((el) => el.getBoundingClientRect())
  if (!rects.length) return
  const r = box.value.getBoundingClientRect()
  const left = Math.min(...rects.map((b) => b.left))
  const right = Math.max(...rects.map((b) => b.right))
  const top = Math.min(...rects.map((b) => b.top))
  chipAt.value = { x: (left + right) / 2 - r.left, y: top - r.top }
}

/* ───────── 클릭: 설비 선택 (배기팬을 눌러도 그 설비) ───────── */
function onClick(event) {
  if (props.editable) return // 공장 관리는 누르는 순간(pointerdown) 고름
  const el = event.target.closest('.eqh')
  if (!el) return
  const eq = byKey(el.getAttribute('data-eq'))
  if (!eq) return
  emit('select', eq)
}
</script>

<template>
  <div class="paint-map-box">
    <!-- SVG 문자열이 저장소 안 고정 그림이라 v-html 사용 (사용자 입력 아님) -->
    <div
      ref="box"
      class="svg"
      :class="{ editable }"
      @mousemove="onMove"
      @mouseleave="onLeave"
      @click="onClick"
      @pointerdown="onDown"
      @pointermove="onDrag"
      @pointerup="onUp"
      @pointercancel="onUp"
      v-html="paintSvg"
    />

    <!-- 이름 칩: 설비 위(이름 · 상태 · 묶인 부품 · 센서 값) -->
    <PaintHoverChip v-if="hover && byKey(hover.key)" :key="hover.key" :equipment="byKey(hover.key)" :at="chipAt" />
  </div>
</template>

<style scoped>
.paint-map-box {
  position: relative;
  width: 100%;
  height: 100%;
}

.svg,
.svg :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}

/* v-html 로 넣은 SVG 에는 scoped 스타일이 안 닿아서 :deep() 사용 */
.svg :deep(.eqh) {
  cursor: pointer;
}

/* 공장 관리: 설비를 잡아 끌 수 있음 */
.svg.editable {
  touch-action: none;
  user-select: none;
}

.svg.editable :deep(.eqh) {
  cursor: grab;
}

/* 칸 이동은 0.15초에 따라감 (설비·상태 테두리는 아래 '마우스 올림'에서 들어 올림 움직임과 같이 정함) */
.svg :deep([id^='tag-']) {
  transition:
    transform 150ms ease-out,
    opacity 250ms;
}

/* 고른 설비: 파란 그림자 (면은 칠하지 않음) */
.svg :deep(.eqh.sel) {
  filter: drop-shadow(0 0 6px rgba(37, 99, 235, 0.9)) drop-shadow(0 0 2px rgba(37, 99, 235, 0.9));
}

/* ───────── 마우스 올림 (paintHover.js, 시안 A) ───────── */
/* 초록 면 덮개: 평소에는 투명, 마우스를 받지 않음 */
.svg :deep(.over) {
  opacity: 0;
  pointer-events: none;
  transition:
    opacity var(--m-hover),
    translate var(--m-panel);
}

/* 1단계: 설비 묶음(부품 + 덮개 + 상태 테두리)을 4px 들어 올리고 덮개를 보임
   - translate 속성을 써서 공장 관리 위치 이동(style.transform)과 겹치지 않게 함 */
.svg :deep(.eqh),
.svg :deep(.halo) {
  transition:
    transform 150ms ease-out,
    opacity 250ms,
    translate var(--m-panel);
}

.svg :deep(.eqh.hov),
.svg :deep(.over.hov),
.svg :deep(.halo.hov) {
  translate: 0 -4px;
}

.svg :deep(.over.hov) {
  opacity: 1;
}

/* 그 설비 센서 이름표 테두리 초록 (주의·경보 이름표는 상태 색 그대로) */
.svg :deep(.tag.hov[data-level='normal'] .pill) {
  stroke: var(--color-level-normal);
  stroke-width: 2.4;
}

/* 센서 이름표의 연결선·점은 마우스를 받지 않음
   (배기팬 한가운데에 CT 이름표 연결점이 있어 설비 올림·누르기를 가로막지 않게) */
.svg :deep(.tag .anchor),
.svg :deep(.tag .lead) {
  pointer-events: none;
}

/* 움직임 줄이기 설정: 들어 올림 없이 색만 */
@media (prefers-reduced-motion: reduce) {
  .svg :deep(.hov) {
    translate: none !important;
  }
}

/* 주의·경보 테두리만 빛번짐 + 천천히 깜빡임 */
.svg :deep(.halo.glow) {
  filter: drop-shadow(0 0 6px currentColor) drop-shadow(0 0 12px currentColor);
  animation: halo-pulse var(--m-pulse-warn) ease-in-out infinite;
}

/* 경보·화재: 0.8초 (화면설계서 R2 --m-pulse-alarm) */
.svg :deep(.halo.glow.fast) {
  animation-duration: var(--m-pulse-alarm);
}

@keyframes halo-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}
</style>
