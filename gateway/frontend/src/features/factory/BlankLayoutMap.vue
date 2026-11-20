<!--
  빈 배치도 — 도장 공정 그림이 없는 공정(공장 관리에서 새로 추가한 공정)에 설비를 놓는 판 (2026-11-20)
  - 격자 바닥(SVG, 1칸 40) 위에 설비를 이름표 상자로 그림. 끌어서 옮기면 격자 칸에 맞춰짐(snap)
  - 위치 pos { x, y } = 이 판의 좌표 (viewBox 1120 × 640, 설비 가운데 점) → DB pos_x · pos_y
    (도장 공정은 PaintMap 그림 좌표를 쓰고, 이 판은 이 판 좌표 — 공정마다 한 가지만 씀)
  - 마우스 좌표 → 판 좌표는 getScreenCTM() 로 바꿈 (화면 배율 CSS zoom 도 들어 있음)
  - 이벤트: select(설비 id), drag(설비 id, { x, y }) — 끄는 동안 계속
-->
<script setup>
import { ref } from 'vue'
import { typeLabel } from './layoutDraft'

const props = defineProps({
  /** [{ id, name, type, pos: { x, y }, active, isNew }] */
  equipment: { type: Array, required: true },
  selectedId: { type: [Number, String], default: null },
  /** 격자에 맞추기 */
  snap: { type: Boolean, default: true },
})

const emit = defineEmits(['select', 'drag'])

// 판 크기 · 격자 · 설비 상자 크기 (판 좌표)
const W = 1120
const H = 640
const CELL = 40
const BOX = { w: 136, h: 56 }

const svg = ref(null)
let dragging = null // { id, dx, dy } 누른 점과 설비 가운데의 차이

/** 마우스 위치 → 판 좌표 */
function toBoard(e) {
  const m = svg.value.getScreenCTM().inverse()
  const p = new DOMPoint(e.clientX, e.clientY).matrixTransform(m)
  return { x: p.x, y: p.y }
}

/** 판 밖으로 나가지 않게, 격자 맞추기면 칸 단위로 */
function clampPos(x, y) {
  const fit = (v) => (props.snap ? Math.round(v / CELL) * CELL : Math.round(v))
  return {
    x: Math.min(W - BOX.w / 2, Math.max(BOX.w / 2, fit(x))),
    y: Math.min(H - BOX.h / 2, Math.max(BOX.h / 2, fit(y))),
  }
}

function onDown(e, eq) {
  emit('select', eq.id)
  const p = toBoard(e)
  dragging = { id: eq.id, dx: p.x - eq.pos.x, dy: p.y - eq.pos.y }
  e.currentTarget.setPointerCapture(e.pointerId)
}

function onMove(e) {
  if (!dragging) return
  const p = toBoard(e)
  emit('drag', dragging.id, clampPos(p.x - dragging.dx, p.y - dragging.dy))
}

function onUp() {
  dragging = null
}
</script>

<template>
  <svg ref="svg" class="board" :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMid meet" role="img" aria-label="설비 배치도">
    <defs>
      <pattern id="blank-grid" :width="CELL" :height="CELL" patternUnits="userSpaceOnUse">
        <path :d="`M ${CELL} 0 L 0 0 0 ${CELL}`" fill="none" stroke="#e2e8f0" stroke-width="1" />
      </pattern>
    </defs>
    <!-- 격자는 판 밖(카드 빈 곳)까지 깔고, 설비를 놓을 수 있는 판은 점선 테두리로 -->
    <rect :x="-W" :y="-H" :width="W * 3" :height="H * 3" fill="url(#blank-grid)" />
    <rect :width="W" :height="H" fill="none" stroke="#cbd5e1" stroke-dasharray="6 5" />

    <g
      v-for="eq in equipment"
      :key="eq.id"
      class="eq"
      :class="{ on: eq.id === selectedId, off: !eq.active, fresh: eq.isNew }"
      :transform="`translate(${eq.pos.x - BOX.w / 2} ${eq.pos.y - BOX.h / 2})`"
      @pointerdown.prevent="onDown($event, eq)"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
    >
      <rect :width="BOX.w" :height="BOX.h" rx="8" />
      <text :x="BOX.w / 2" y="24" class="nm">{{ eq.name.length > 9 ? `${eq.name.slice(0, 9)}…` : eq.name }}</text>
      <text :x="BOX.w / 2" y="42" class="tp">{{ eq.isNew ? '새 설비' : typeLabel(eq.type) }}</text>
    </g>
  </svg>
</template>

<style scoped>
.board {
  width: 100%;
  height: 100%;
  display: block;
  overflow: visible; /* 판 밖 격자까지 보이게 (카드 .map 이 잘라 줌) */
  background: #f8fafc;
  touch-action: none;
}

.eq {
  cursor: grab;
}

.eq:active {
  cursor: grabbing;
}

.eq rect {
  fill: #fff;
  stroke: #94a3b8;
  stroke-width: 1.5;
  filter: drop-shadow(0 2px 4px rgba(15, 23, 42, 0.08));
  transition: stroke 150ms;
}

.eq:hover rect {
  stroke: var(--color-primary);
}

/* 새 설비(저장 전): 파란 점선 */
.eq.fresh rect {
  stroke: var(--color-primary);
  stroke-dasharray: 5 4;
}

/* 고른 설비: 파란 테두리 + 옅은 파랑 */
.eq.on rect {
  fill: var(--color-primary-soft);
  stroke: var(--color-primary);
  stroke-width: 2.5;
  stroke-dasharray: none;
}

/* 사용 안 함: 흐리게 */
.eq.off {
  opacity: 0.45;
}

text {
  text-anchor: middle;
  pointer-events: none;
  user-select: none;
}

.nm {
  font-size: 15px;
  font-weight: 600;
  fill: var(--color-text);
}

.tp {
  font-size: 12px;
  fill: var(--color-text-sub);
}
</style>
