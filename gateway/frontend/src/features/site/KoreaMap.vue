<!--
  사업장 지도 (남한 지도 + 사업장 핀)
  - 지도 그림: 화면설계안 assets/korea.js 를 정적 SVG 로 옮긴 것 (assets/images/korea-map.svg)
  - 핀 위치: 설계안 지도 좌표(KOREA.pins) 그대로. 지도 영역(VIEWBOX)도 설계안 사업장 선택 화면과 같음
  - 핀을 누르면 select 이벤트로 사업장 ID 를 알림
-->
<script setup>
import { computed } from 'vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { LEVEL, LEVEL_META } from '@/types/codes'
import koreaMap from '@/assets/images/korea-map.svg'

const props = defineProps({
  /** [{ id, code, name, level }] */
  sites: { type: Array, required: true },
  /** 강조할 사업장 ID (파란 테두리) */
  currentId: { type: Number, default: null },
})

const emit = defineEmits(['select'])

// 지도 SVG 의 viewBox (x, y, 너비, 높이) — korea-map.svg 와 같은 값
const VIEWBOX = { x: 258, y: 432, w: 416, h: 328 }

/**
 * 사업장 코드별 핀 좌표(지도 SVG 좌표)와 이름표 배치
 * - shiftX: 이름표를 옆으로 옮길 거리(px), below: 이름표를 점 아래에 둘지
 * - 코드는 서버 값 그대로 (중앙 시드 plant.code: USN · ASN · JJU). 2026-11-20 전까지는 설계안 예시 값 ULS-01 등을 써서 실제 서버에서 핀이 안 나왔음
 * - 지도 그림의 좌표계라 위경도로 바로 계산할 수 없음 → 새 사업장이 생기면 여기에 좌표 추가 필요
 */
const PIN_LAYOUT = {
  USN: { x: 586.1, y: 608, shiftX: -80 },
  ASN: { x: 380.5, y: 542.3, shiftX: -92 },
  JJU: { x: 406.7, y: 597.5, shiftX: 0, below: true },
}

// 화면에 그릴 핀 목록: 좌표를 % 로 바꿔서 지도 크기가 바뀌어도 같은 자리에 있게 함
const pins = computed(() =>
  props.sites
    .filter((site) => PIN_LAYOUT[site.code])
    .map((site, index) => {
      const p = PIN_LAYOUT[site.code]
      return {
        ...site,
        ...p,
        index,
        left: ((p.x - VIEWBOX.x) / VIEWBOX.w) * 100,
        top: ((p.y - VIEWBOX.y) / VIEWBOX.h) * 100,
        color: LEVEL_META[site.level]?.color ?? 'var(--color-text-weak)',
        isNormal: site.level === LEVEL.NORMAL,
      }
    }),
)
</script>

<template>
  <div class="korea-map">
    <img :src="koreaMap" alt="" class="map-img" />

    <template v-for="pin in pins" :key="pin.id">
      <!-- 현재 사업장 둘레 물결: 정상이면 파랑, 이상이면 상태 색 -->
      <template v-if="pin.id === currentId">
        <span
          class="ring"
          :class="{ live: pin.isNormal }"
          :style="{ left: `${pin.left}%`, top: `${pin.top}%`, '--ring': pin.isNormal ? 'rgba(37,99,235,.5)' : pin.color }"
        />
        <span
          v-if="!pin.isNormal"
          class="ring live big"
          :style="{ left: `${pin.left}%`, top: `${pin.top}%`, '--ring': pin.color }"
        />
      </template>

      <button
        type="button"
        class="pin"
        :class="{ current: pin.id === currentId, below: pin.below, abnormal: !pin.isNormal }"
        :style="{ left: `${pin.left}%`, top: `${pin.top}%`, '--j': pin.index, '--level': pin.color }"
        @click="emit('select', pin.id)"
      >
        <span class="label" :style="{ marginLeft: `${pin.shiftX}px` }">
          {{ pin.name }}
          <LevelBadge :level="pin.level" />
        </span>
        <span class="stem" />
        <span class="dot" />
      </button>
    </template>
  </div>
</template>

<style scoped>
.korea-map {
  position: relative;
  width: 100%;
  aspect-ratio: 416 / 328;
  overflow: hidden;
}

.map-img {
  display: block;
  width: 100%;
  height: 100%;
}

/* 핀: 점이 좌표에 오도록 아래 끝을 기준으로 배치 */
.pin {
  position: absolute;
  margin-top: 7px;
  transform: translate(-50%, -100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0;
  border: none;
  background: none;
  white-space: nowrap;
  cursor: pointer;
  animation: pindrop 0.5s cubic-bezier(0.2, 1.2, 0.3, 1) both;
  animation-delay: calc(300ms + var(--j, 0) * 110ms);
}

.pin.below {
  margin-top: -7px;
  transform: translate(-50%, 0);
  flex-direction: column-reverse;
}

.label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px 7px 12px;
  background: #fff;
  border: 1.5px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  box-shadow: 0 3px 10px rgba(15, 23, 42, 0.14);
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  transition:
    border-color 0.15s,
    transform 0.15s;
}

.pin:hover .label {
  transform: translateY(-2px);
  border-color: #93c5fd;
}

.pin.current .label {
  border-color: var(--color-primary);
  box-shadow:
    0 0 0 3px rgba(37, 99, 235, 0.18),
    0 3px 10px rgba(15, 23, 42, 0.14);
}

.pin.abnormal .label {
  border-color: var(--level);
}

.stem {
  width: 2px;
  height: 38px;
  background: #64748b;
}

.pin.below .stem {
  height: 26px;
}

.dot {
  width: 14px;
  height: 14px;
  margin: -2px 0;
  border-radius: 50%;
  border: 3px solid #fff;
  background: var(--level);
  box-shadow: 0 0 0 1.5px var(--level);
}

/* 현재 사업장 물결 (위에서 본 원처럼 납작하게) */
.ring {
  position: absolute;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 2px solid var(--ring);
  background: color-mix(in srgb, var(--ring) 12%, transparent);
  transform: translate(-50%, -50%) scaleY(0.6);
  pointer-events: none;
}

.ring.live {
  width: 90px;
  height: 90px;
  background: none;
  animation: ripple 2s ease-out infinite;
}

.ring.big {
  width: 170px;
  height: 170px;
  animation-duration: 1.2s;
}

@keyframes ripple {
  0% {
    transform: translate(-50%, -50%) scaleY(0.6) scale(0.5);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -50%) scaleY(0.6) scale(1.5);
    opacity: 0;
  }
}

@keyframes pindrop {
  from {
    opacity: 0;
    margin-top: -26px;
  }
}
</style>
