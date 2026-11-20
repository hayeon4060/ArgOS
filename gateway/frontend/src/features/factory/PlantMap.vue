<!--
  5공장 공정 배치도 (공정 건물 4개 + 이름표 + 열화상 카메라 표시)
  - 그림: 화면설계안 lib.js plantMap() 과 같은 계산으로 만든 정적 SVG (assets/images/plant-map.svg)
  - 공정 상태에 따라 건물 둘레 색을 바꿈 (SVG 안 .hall-zone 의 data-level)
  - 이름표·카메라 표시·요약 말풍선은 HTML 로 그림 위에 겹쳐서 배치 (좌표는 plantLayout.js)
  - 건물이나 이름표를 누르면 select 이벤트로 공정을 알림
-->
<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { HALLS, VIEWBOX } from './plantLayout'
import plantSvg from '@/assets/images/plant-map.svg?raw'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { LEVEL, LEVEL_META } from '@/types/codes'
import { blinkClass, blinkKey } from '@/utils/blink'

const props = defineProps({
  /** 공장 현황의 공정 목록 [{ id, code, name, level, equipmentCount, equipmentLevels, sensorCount }] */
  processes: { type: Array, required: true },
  /** 열화상 카메라 [{ tag, processId }] */
  cameras: { type: Array, default: () => [] },
})

const emit = defineEmits(['select'])

/** 그림 안 건물 이름 ↔ 공정 코드 (그림이 설계안 고정 그림이라 프론트에 짝을 적어 둠) */
const HALL_BY_CODE = { PRS: 'press', BDY: 'body', PNT: 'paint', ASM: 'assy' }

// 상태 → 그림이 쓰는 이름 (plant-map.svg 의 data-level 값)
const HALL_LEVEL = { [LEVEL.NORMAL]: 'normal', [LEVEL.WARN]: 'warn', [LEVEL.ALARM]: 'danger', [LEVEL.FIRE]: 'danger' }

// 그림을 담는 칸의 가로세로 비율 (설계안 1236 × 534)
const BOX_RATIO = 1236 / 534

/**
 * 그림 좌표 → 칸 안 위치(%)
 * - 그림은 칸 안에 비율을 지키며 들어가서(meet) 좌우에 빈 곳이 생김 → 그만큼 더해서 계산
 */
function toPercent([x, y]) {
  const ratio = VIEWBOX.w / VIEWBOX.h
  if (BOX_RATIO > ratio) {
    const used = ratio / BOX_RATIO // 그림이 차지하는 가로 비율
    return { left: ((1 - used) / 2 + ((x - VIEWBOX.x) / VIEWBOX.w) * used) * 100, top: ((y - VIEWBOX.y) / VIEWBOX.h) * 100 }
  }
  const used = BOX_RATIO / ratio // 그림이 차지하는 세로 비율
  return { left: ((x - VIEWBOX.x) / VIEWBOX.w) * 100, top: ((1 - used) / 2 + ((y - VIEWBOX.y) / VIEWBOX.h) * used) * 100 }
}

// 화면에 그릴 이름표 목록: 공정 + 그림 좌표 + 카메라 태그
const labels = computed(() =>
  props.processes
    .filter((p) => HALLS[HALL_BY_CODE[p.code]])
    .map((p) => {
      const hall = HALLS[HALL_BY_CODE[p.code]]
      const camera = props.cameras.find((c) => c.processId === p.id)
      return {
        process: p,
        label: toPercent(hall.center),
        camera: toPercent(hall.camera),
        cameraTag: camera?.tag ?? hall.ir,
        color: LEVEL_META[p.level]?.color,
      }
    }),
)

// 정상이 아닌 공정은 이름표 아래에 요약 말풍선 (설계안: 주의 이상일 때 도장 공정 요약)
const abnormalLabels = computed(() => labels.value.filter((l) => l.process.level !== LEVEL.NORMAL))

const box = ref(null)

/** 공정 상태를 그림 건물 색에 반영 */
function applyLevels() {
  const svg = box.value?.querySelector('svg')
  if (!svg) return
  for (const p of props.processes) {
    const el = svg.querySelector(`.hall-zone[data-hall="${HALL_BY_CODE[p.code]}"]`)
    el?.setAttribute('data-level', HALL_LEVEL[p.level] ?? 'normal')
    // 점멸 단계 (정상이면 지움) — 테두리 굵게 + 빛 크게, 아래 스타일
    const blink = blinkKey(p.level)
    if (blink) el?.setAttribute('data-blink', blink)
    else el?.removeAttribute('data-blink')
  }
}

/** 그림의 건물을 눌렀을 때 */
function onMapClick(event) {
  const hall = event.target.closest('.hall-zone')?.getAttribute('data-hall')
  const code = Object.keys(HALL_BY_CODE).find((c) => HALL_BY_CODE[c] === hall)
  const process = props.processes.find((p) => p.code === code)
  if (process) emit('select', process)
}

/** 요약 말풍선의 주의·경보 설비 수 */
const abnormalEquipment = (p) => (p.equipmentLevels?.[LEVEL.WARN] ?? 0) + (p.equipmentLevels?.[LEVEL.ALARM] ?? 0)

onMounted(applyLevels)
watch(() => props.processes, () => nextTick(applyLevels), { deep: true })
</script>

<template>
  <div class="plant-map-box">
    <!-- SVG 문자열이 저장소 안 고정 그림이라 v-html 사용 (사용자 입력 아님) -->
    <div ref="box" class="svg" @click="onMapClick" v-html="plantSvg" />

    <template v-for="l in labels" :key="l.process.id">
      <!-- 공정 이름표 -->
      <button
        type="button"
        class="label"
        :class="l.process.level !== LEVEL.NORMAL && ['blink-glow', 'weak', blinkClass(l.process.level)]"
        :style="{ left: `${l.label.left}%`, top: `${l.label.top}%`, borderColor: l.color }"
        @click="emit('select', l.process)"
      >
        {{ l.process.name }}
        <LevelBadge :level="l.process.level" class="big" />
      </button>
      <!-- 열화상 카메라 태그 -->
      <span class="camera mono" :style="{ left: `${l.camera.left}%`, top: `${l.camera.top}%` }">{{ l.cameraTag }}</span>
    </template>

    <!-- 정상이 아닌 공정 요약 -->
    <button
      v-for="l in abnormalLabels"
      :key="`pop-${l.process.id}`"
      type="button"
      class="summary"
      :style="{ left: `${l.label.left}%`, top: `${l.label.top}%` }"
      @click="emit('select', l.process)"
    >
      <span>설비 <b class="mono">{{ l.process.equipmentCount }}</b></span>
      <span>센서 <b class="mono">{{ l.process.sensorCount }}</b></span>
      <span>
        {{ LEVEL_META[l.process.level].label }} 설비
        <b class="mono" :style="{ color: `var(--color-level-${LEVEL_META[l.process.level].key}-text)` }">
          {{ abnormalEquipment(l.process) }}
        </b>
      </span>
      <span class="go">3D 설비 배치도 <AppIcon name="cr" :size="16" /></span>
    </button>
  </div>
</template>

<style scoped>
/* 설계안 지도 칸 비율 (1236 × 534) */
.plant-map-box {
  position: relative;
  aspect-ratio: 1236 / 534;
}

.svg,
.svg :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}

/* 공정 이름표: 건물 지붕 가운데 */
.label {
  position: absolute;
  transform: translate(-50%, calc(-50% - 8px));
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 6px 12px;
  border: 1.5px solid;
  border-radius: var(--radius-sm);
  background: #fff;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.1);
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}

/*
 * 주의·경보·화재 공정 건물: 테두리 굵게 + 빛 크게 (blink.css 의 blink-stroke · blink-light)
 * - 그림의 둘레 선 10 ↔ 16(× 단계 배수), 바깥 빛 30% ↔ 100%
 * - 공정 이름표는 같은 박자로 약하게 빛번짐 (blink-glow weak)
 */
.svg :deep(.hall-zone[data-blink] .rim polygon) {
  --blink-w0: 8px;
  --blink-w1: 16px;
  animation: blink-stroke var(--blink-t) ease-in-out infinite;
}

.svg :deep(.hall-zone[data-blink] .glow) {
  transform-box: fill-box;
  transform-origin: center;
  animation: blink-light var(--blink-t) ease-in-out infinite;
}

/* 마우스 올림: 깜빡임 멈추고 가장 진한 모양으로 고정 */
.svg :deep(.hall-zone[data-blink]:hover .rim polygon) {
  animation: none;
  stroke-width: 16px;
}

.svg :deep(.hall-zone[data-blink]:hover .glow) {
  animation: none;
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .svg :deep(.hall-zone[data-blink] .rim polygon) {
    animation: none;
    stroke-width: 16px;
  }

  .svg :deep(.hall-zone[data-blink] .glow) {
    animation: none;
    opacity: 1;
  }
}

/* 이름표 안 상태 배지는 조금 크게 (설계안 chip.lgc) */
.label :deep(.level-badge.big) {
  padding: 4px 12px;
  font-size: 14px;
  line-height: 20px;
}

/* 열화상 카메라 태그 (분홍) */
.camera {
  position: absolute;
  transform: translate(18px, -14px);
  padding: 1px 7px;
  border: 1px solid #f9a8d4;
  border-radius: var(--radius-sm);
  background: #fff;
  color: #be185d;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  pointer-events: none;
}

/* 요약 말풍선: 이름표 아래 */
.summary {
  position: absolute;
  z-index: 2;
  width: 340px;
  transform: translate(-50%, 30px);
  display: flex;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
  font-size: 13px;
  color: var(--color-text-sub);
  white-space: nowrap;
  cursor: pointer;
  animation: pop-in var(--m-pop-in) both; /* 4px 위에서 내려오며 나타남 (motion.css) */
}

.summary b {
  color: var(--color-text);
}

.summary .go {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  color: var(--color-primary);
  font-weight: 700;
}
</style>
