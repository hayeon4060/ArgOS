<!--
  스캔 원본 보기 (화면설계안 vInspect() viewer() · docT())
  - 위쪽 줄: 파일 이름, 범례(인식한 항목 파랑 / 확인 필요 주황), 쪽
  - 원본 위에 인식한 칸 상자 (box 는 그림 크기에 대한 비율 — 명세서 E1)
    · 확인 필요 칸은 주황 테두리가 천천히 깜빡이고 '확인' 꼬리표
    · 상자를 누르면 오른쪽 인식 결과의 그 칸으로 (pick), 결과 칸에 들어가면 상자가 진해짐 (hlKey)
    · 처음 결과가 나올 때 상자가 차례로 튀어나옴 (45ms 간격)
  - 올리는 중: 돌아가는 고리 + %, 인식 중: 원본 위를 훑는 파란 띠(1.7초 반복), 인식 실패: 안내 + 다시 인식
  - 확대·축소 단추(50 ~ 125%), 끌어서 이동, 휠로 위아래 이동
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { toLayoutPx } from '@/utils/screenScale'

const props = defineProps({
  fileName: { type: String, default: '' },
  imageUrl: { type: String, default: null },
  /** empty 파일 없음 · up 올리는 중 · scan 인식 중 · fail 인식 실패 · review 결과 */
  mode: { type: String, required: true },
  uploadPct: { type: Number, default: 0 },
  progress: { type: Number, default: 0 },
  /** 인식 결과 칸 [{ key, box, needCheck, checked, edited }] */
  fields: { type: Array, default: () => [] },
  /** 지금 입력 중인 칸 (상자 강조) */
  hlKey: { type: String, default: '' },
  /** 결과가 막 나왔을 때 상자 튀어나옴 */
  reveal: { type: Boolean, default: false },
  failMessage: { type: String, default: '' },
})

const emit = defineEmits(['pick', 'retry'])

// 원본 그림 크기 (설계안 910 × 1286)
const W = 910
const H = 1286
const ZOOMS = [0.5, 0.645, 0.8, 1, 1.25]

const vw = ref(null)
const size = ref({ w: 626, h: 856 })
const zoom = ref(0.645)
const pan = ref([0, 0])
const animate = ref(false)

/** 보기 영역 크기 재기 (화면 폭에 따라 바뀜) */
function measure() {
  if (!vw.value) return
  size.value = { w: vw.value.clientWidth, h: vw.value.clientHeight - 42 }
}
let ro
onMounted(() => {
  measure()
  ro = new ResizeObserver(measure)
  ro.observe(vw.value)
})
onBeforeUnmount(() => ro?.disconnect())

// 새 서류면 처음 배율로
watch(
  () => props.fileName,
  () => {
    zoom.value = 0.645
    pan.value = [0, 0]
  },
)

/** 그림 위치: 보기 영역보다 작으면 가운데, 크면 끌어서 이동한 만큼 (가장자리 12px 까지) */
const transform = computed(() => {
  const { w: vw0, h: vh } = size.value
  const w = W * zoom.value
  const h = H * zoom.value
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v))
  const x = w <= vw0 ? (vw0 - w) / 2 : clamp((vw0 - w) / 2 + pan.value[0], vw0 - w - 12, 12)
  const y = h <= vh ? (vh - h) / 2 : clamp((vh - h) / 2 + pan.value[1], vh - h - 12, 12)
  return `translate(${x}px, ${y + 42}px) scale(${zoom.value}) rotate(-0.35deg)`
})
const canPan = computed(() => W * zoom.value > size.value.w || H * zoom.value > size.value.h)

function zoomBy(step) {
  const i = ZOOMS.indexOf(zoom.value) + step
  if (i < 0 || i >= ZOOMS.length) return
  animate.value = true
  zoom.value = ZOOMS[i]
  setTimeout(() => (animate.value = false), 320)
}

/* 끌어서 이동 */
let drag = null
function down(e) {
  if (!canPan.value || e.target.closest('.zoom, .bx, .vt, button')) return
  drag = { x: e.clientX, y: e.clientY, pan: [...pan.value] }
  vw.value.setPointerCapture(e.pointerId)
}
function move(e) {
  if (!drag) return
  // 마우스가 움직인 거리(확대된 화면 기준)를 CSS px 로 바꿔서 더함
  pan.value = [drag.pan[0] + toLayoutPx(e.clientX - drag.x), drag.pan[1] + toLayoutPx(e.clientY - drag.y)]
}
const up = () => (drag = null)
function wheel(e) {
  if (H * zoom.value <= size.value.h) return
  e.preventDefault()
  pan.value = [pan.value[0], pan.value[1] - e.deltaY]
}

/** 원본 위 상자 (비율 → 그림 위 px) */
const boxes = computed(() =>
  props.fields
    .filter((f) => f.box)
    .map((f, i) => ({
      key: f.key,
      label: f.label,
      i,
      lo: f.needCheck && !f.checked && !f.edited,
      style: { left: `${f.box[0] * W}px`, top: `${f.box[1] * H}px`, width: `${f.box[2] * W}px`, height: `${f.box[3] * H}px` },
    })),
)
</script>

<template>
  <div
    ref="vw"
    class="vw"
    :class="{ grab: canPan && mode === 'review' }"
    @pointerdown="down"
    @pointermove="move"
    @pointerup="up"
    @pointercancel="up"
    @wheel="wheel"
  >
    <div class="vt">
      <AppIcon name="scan" :size="16" />
      <b>{{ fileName || '파일 없음' }}</b>
      <span class="lgd">
        <span><i class="k blue" />인식한 항목</span>
        <span><i class="k orange" />확인 필요</span>
      </span>
      <span class="sep">|</span>
      <span>1 / 1쪽</span>
    </div>

    <div class="doc" :class="{ blank: mode !== 'review' && mode !== 'scan', anim: animate }" :style="{ transform }">
      <!-- 올리는 중 -->
      <div v-if="mode === 'up'" class="upl">
        <span class="spin-dk" />
        <b>파일을 올리는 중입니다</b>
        <span class="mono pct">{{ uploadPct }}%</span>
      </div>
      <!-- 인식 실패 -->
      <div v-else-if="mode === 'fail'" class="upl" role="alert">
        <span class="fail-ico"><AppIcon name="alert" :size="28" /></span>
        <b>글자를 읽지 못했습니다</b>
        <span class="msg">{{ failMessage || '더 선명하게 스캔해 다시 올리세요.' }}</span>
        <button type="button" class="btn retry" @click="emit('retry')"><AppIcon name="redo" :size="22" />다시 인식</button>
      </div>
      <!-- 파일 없음 -->
      <div v-else-if="mode === 'empty' || !imageUrl" class="upl">
        <span class="fail-ico gray"><AppIcon name="file" :size="28" /></span>
        <b>{{ mode === 'empty' ? '오른쪽에서 서류를 올리거나 골라 주세요' : '원본을 불러오는 중입니다' }}</b>
      </div>
      <template v-else>
        <img :src="imageUrl" alt="스캔한 서류 원본" draggable="false" />
        <!-- 인식 중: 훑는 띠 -->
        <template v-if="mode === 'scan'">
          <div class="scanv" />
          <div class="scanl" />
        </template>
        <!-- 인식한 칸 -->
        <template v-else>
          <button
            v-for="b in boxes"
            :key="b.key"
            type="button"
            class="bx"
            :class="{ lo: b.lo, hl: hlKey === b.key, pop: reveal }"
            :style="{ ...b.style, animationDelay: reveal ? `${b.i * 45}ms` : undefined }"
            :data-c="b.lo ? '확인' : ''"
            :aria-label="`${b.label} 칸으로 이동`"
            @click="emit('pick', b.key)"
          />
        </template>
      </template>
    </div>

    <div v-if="mode === 'scan'" class="scan-tag mono">인식 중 {{ progress }}%</div>

    <div class="zoom">
      <button type="button" aria-label="축소" @click="zoomBy(-1)">−</button>
      <span class="mono">{{ Math.round(zoom * 100) }}%</span>
      <button type="button" aria-label="확대" @click="zoomBy(1)">+</button>
    </div>
  </div>
</template>

<style scoped>
.vw {
  position: relative;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #e5e9ef;
  touch-action: none;
}

.vw.grab {
  cursor: grab;
}

.vt {
  position: absolute;
  inset: 0 0 auto;
  z-index: 2;
  height: 42px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 14px;
  border-bottom: 1px solid var(--color-border);
  background: #fff;
  font-size: 13px;
  color: var(--color-text-sub);
}

.vt b {
  max-width: 230px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--color-text);
}

.lgd {
  margin-left: auto;
  display: flex;
  gap: 14px;
}

.k {
  display: inline-block;
  width: 14px;
  height: 10px;
  margin-right: 6px;
  vertical-align: -1px;
  border-radius: 2px;
}

.k.blue {
  border: 1.5px solid var(--color-primary);
  background: var(--color-primary-soft);
}

.k.orange {
  border: 1.5px solid var(--color-level-warn);
  background: var(--color-level-warn-soft);
}

.sep {
  color: #cbd5e1;
}

.doc {
  position: absolute;
  top: 0;
  left: 0;
  width: 910px;
  height: 1286px;
  transform-origin: 0 0;
  background: #fff;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.16);
  user-select: none;
}

.doc.anim {
  transition: transform 300ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.doc.blank {
  background: var(--color-surface-sub);
}

.doc img {
  display: block;
  width: 910px;
  height: 1286px;
  filter: contrast(1.04) sepia(0.07) brightness(0.985);
  pointer-events: none;
}

/* 원본 크기(910px) 기준이라 글자를 크게 씀 — 줄어들면 보통 크기 */
.upl {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  font-size: 30px;
  color: #334155;
}

.pct {
  font-size: 28px;
  color: #64748b;
}

.msg {
  font-size: 24px;
  line-height: 36px;
  color: #64748b;
  text-align: center;
}

.spin-dk {
  width: 60px;
  height: 60px;
  border: 5px solid #e2e8f0;
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite; /* base.css */
}

.fail-ico {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm);
}

.fail-ico.gray {
  background: #eef2f6;
  color: var(--color-text-weak);
}

.retry {
  height: 64px;
  margin-top: 10px;
  padding: 0 30px;
  font-size: 24px;
}

/* 인식 중: 옅은 파란 막 + 위아래로 훑는 띠 */
.scanv {
  position: absolute;
  inset: 0;
  background: rgba(37, 99, 235, 0.06);
}

.scanl {
  position: absolute;
  left: 0;
  right: 0;
  height: 6px;
  background: linear-gradient(90deg, transparent, #2563eb 15%, #60a5fa 50%, #2563eb 85%, transparent);
  box-shadow: 0 0 24px 6px rgba(37, 99, 235, 0.45);
  animation: scanline 1.7s ease-in-out infinite alternate;
}

@keyframes scanline {
  from {
    top: 0;
  }
  to {
    top: calc(100% - 6px);
  }
}

.scan-tag {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 2;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: rgba(15, 23, 42, 0.75);
  font-size: 12.5px;
  color: #fff;
}

/* 인식한 칸 */
.bx {
  position: absolute;
  padding: 0;
  border: 2px solid rgba(37, 99, 235, 0.5);
  border-radius: 3px;
  background: rgba(37, 99, 235, 0.05);
  cursor: pointer;
  transition:
    border-color 200ms,
    background 200ms,
    box-shadow 200ms;
}

.bx.hl {
  border-color: var(--color-primary);
  background: rgba(37, 99, 235, 0.16);
  box-shadow: 0 0 0 5px rgba(37, 99, 235, 0.22);
}

.bx.lo {
  border: 3px solid var(--color-level-warn);
  background: rgba(249, 115, 22, 0.13);
  animation: hot-warn 1.6s ease-in-out infinite;
}

.bx.lo.hl {
  background: rgba(249, 115, 22, 0.26);
}

.bx.lo::after {
  content: attr(data-c);
  position: absolute;
  top: -33px;
  left: -3px;
  padding: 2px 7px;
  border-radius: 3px;
  background: #ea580c;
  font: 600 19px var(--font-mono);
  color: #fff;
}

.bx.pop {
  animation: box-pop 300ms cubic-bezier(0.2, 1.2, 0.3, 1) both;
}

.bx.pop.lo {
  animation:
    box-pop 300ms cubic-bezier(0.2, 1.2, 0.3, 1) both,
    hot-warn 1.6s ease-in-out 300ms infinite;
}

@keyframes box-pop {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes hot-warn {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.45);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(249, 115, 22, 0);
  }
}

.zoom {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 2;
  display: flex;
  overflow: hidden;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
}

.zoom > * {
  height: 32px;
  min-width: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
  border: none;
  border-right: 1px solid var(--color-border);
  background: none;
  font-size: 13px;
  color: var(--color-text-sub);
}

.zoom > :last-child {
  border: none;
}

.zoom button {
  cursor: pointer;
}

.zoom button:hover {
  background: #f1f5f9;
}

@media (prefers-reduced-motion: reduce) {
  .scanl,
  .bx.lo,
  .bx.pop {
    animation: none;
  }
}
</style>
