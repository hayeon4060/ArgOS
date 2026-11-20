<!--
  2초 길게 누르기 버튼 (화면설계안 js/core.js holdButton(), 화면설계서 C1 · R2 --m-hold)
  - 마우스·터치·스페이스바·엔터로 누르고 있으면 둘레 고리가 2초에 걸쳐 참(선형) → 다 차면 done
  - 중간에 떼면 취소, 고리가 0.2초에 0으로 돌아감
  - 누르기 전에는 가운데 동그라미가 천천히 숨쉬듯 커졌다 작아짐(1.8초), 다 차면 한 번 튐
  - 끝나는 시점은 '누른 시각 + 2초' 타이머로 정함 (화면 그리기가 느려져도 정확히 2초)
  - 다 차면 다시 눌리지 않음. 부모가 reset() 을 부르면 처음 상태로 돌아가 다시 누를 수 있음
    (예: 셧다운 확인을 취소했거나 요청이 실패했을 때 — ShutdownPanel)
  - 사용 예: <HoldButton :size="148" color="#F97316" label="길게 눌러 확인" @progress="..." @done="..." />
-->
<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  size: { type: Number, default: 148 },
  color: { type: String, required: true },
  label: { type: String, required: true },
  /** 눌러야 하는 시간 (ms) */
  ms: { type: Number, default: 2000 },
  disabled: { type: Boolean, default: false },
  /** 가운데 아이콘: 'touch'(손가락, 기본) 또는 'power'(셧다운) */
  icon: { type: String, default: 'touch' },
})

const emit = defineEmits(['progress', 'done'])

const STROKE = 10
const r = computed(() => (props.size - STROKE) / 2)
const length = computed(() => 2 * Math.PI * r.value)

const progress = ref(0) // 0 ~ 1
const holding = ref(false)
const done = ref(false)
const releasing = ref(false) // 손을 떼서 0으로 돌아가는 중 (고리에 0.2초 움직임)

let startAt = 0
let frame = 0
let doneTimer = 0
let emitTimer = 0 // 다 찬 뒤 done 을 알리기까지 잠깐 기다리는 타이머

function draw() {
  progress.value = Math.min(1, (performance.now() - startAt) / props.ms)
  emit('progress', progress.value * props.ms)
  if (holding.value) frame = requestAnimationFrame(draw)
}

function start(e) {
  if (done.value || holding.value || props.disabled) return
  e?.preventDefault()
  releasing.value = false
  holding.value = true
  startAt = performance.now()
  frame = requestAnimationFrame(draw)
  doneTimer = setTimeout(finish, props.ms)
}

function finish() {
  cancelAnimationFrame(frame)
  holding.value = false
  progress.value = 1
  done.value = true
  emit('progress', props.ms)
  emitTimer = setTimeout(() => emit('done'), 180) // 체크 표시가 튀는 것을 잠깐 보여 준 뒤
}

/** 처음 상태로 되돌림 (다 찬 뒤에도 다시 누를 수 있게) — 부모가 ref 로 부름 */
function reset() {
  cancelAnimationFrame(frame)
  clearTimeout(doneTimer)
  clearTimeout(emitTimer)
  holding.value = false
  done.value = false
  releasing.value = true // 고리가 0.2초에 걸쳐 0으로
  progress.value = 0
  emit('progress', 0)
}

defineExpose({ reset })

function stop() {
  if (!holding.value || done.value) return
  cancelAnimationFrame(frame)
  clearTimeout(doneTimer)
  holding.value = false
  releasing.value = true
  progress.value = 0
  emit('progress', 0)
}

function onKeydown(e) {
  if ((e.key === ' ' || e.key === 'Enter') && !e.repeat) start(e)
}

function onKeyup(e) {
  if (e.key === ' ' || e.key === 'Enter') stop()
}

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  clearTimeout(doneTimer)
  clearTimeout(emitTimer)
})
</script>

<template>
  <button
    type="button"
    class="hold"
    :class="{ idle: !holding && !done, done }"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :aria-label="`${label} (2초 동안 누르고 있기)`"
    :disabled="disabled"
    @pointerdown="start"
    @pointerup="stop"
    @pointerleave="stop"
    @pointercancel="stop"
    @keydown="onKeydown"
    @keyup="onKeyup"
  >
    <svg :width="size" :height="size" class="ring" aria-hidden="true">
      <circle :cx="size / 2" :cy="size / 2" :r="r" fill="none" stroke="#e2e8f0" :stroke-width="STROKE" />
      <circle
        class="bar"
        :class="{ releasing }"
        :cx="size / 2"
        :cy="size / 2"
        :r="r"
        fill="none"
        :stroke="color"
        :stroke-width="STROKE"
        stroke-linecap="round"
        :stroke-dasharray="`${length * progress} ${length}`"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
      />
    </svg>
    <span class="core" :style="{ inset: `${STROKE + 8}px`, background: color }">
      <!-- 손가락 아이콘 (설계안 touch) / 다 차면 체크 -->
      <svg v-if="!done && icon === 'power'" viewBox="0 0 24 24" :width="size * 0.2" :height="size * 0.2" class="icon" aria-hidden="true">
        <path d="M12 2v10" /><path d="M18.4 6.6a9 9 0 1 1-12.77.04" />
      </svg>
      <svg v-else-if="!done" viewBox="0 0 24 24" :width="size * 0.2" :height="size * 0.2" class="icon" aria-hidden="true">
        <path d="M18 11V6a2 2 0 0 0-4 0" /><path d="M14 10V4a2 2 0 0 0-4 0v2" /><path d="M10 10.5V6a2 2 0 0 0-4 0v8" />
        <path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15" />
      </svg>
      <svg v-else viewBox="0 0 24 24" :width="size * 0.26" :height="size * 0.26" class="icon" aria-hidden="true">
        <path d="M20 6 9 17l-5-5" />
      </svg>
      <b :style="{ fontSize: `${Math.max(13, size * 0.085)}px` }">{{ label }}</b>
    </span>
  </button>
</template>

<style scoped>
.hold {
  position: relative;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: none;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  transition: transform 150ms;
}

.hold:active {
  transform: scale(0.97);
}

.hold:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.ring {
  position: absolute;
  inset: 0;
}

/* 손을 떼면 0.2초에 걸쳐 0으로 */
.bar.releasing {
  transition: stroke-dasharray 200ms ease-out;
}

.core {
  position: absolute;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: filter 150ms;
}

.hold:hover .core {
  filter: brightness(1.06);
}

.icon {
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.core b {
  margin-top: 6px;
  white-space: nowrap;
}

/* 누르기 전: 숨쉬듯 (설계안 holdIdle 1.8초) */
.idle .core {
  animation: hold-idle 1.8s ease-in-out infinite;
}

/* 다 찼을 때 한 번 튐 */
.done .core {
  animation: hold-pop 350ms ease-out;
}

@keyframes hold-idle {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.04);
  }
}

@keyframes hold-pop {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.14);
  }
}

@media (prefers-reduced-motion: reduce) {
  .idle .core,
  .done .core {
    animation: none;
  }
}
</style>
