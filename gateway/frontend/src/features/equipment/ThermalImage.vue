<!--
  열화상 그림 한 장 (화면설계안 js/lib.js irBox())
  - 왼쪽 위: LIVE(깜빡이는 빨간 점) 또는 SNAPSHOT + 구역 이름 / 왼쪽 아래: 촬영 시각(있으면)
  - 오른쪽: 색 눈금과 범위 글자
  - 최고 온도 지점: 흰 네모 표시(1.4초마다 퍼짐) + 값 말풍선
  - 설비 상세 '열화상 카메라' 카드와 주의 상세 창(열화상 이미지)이 같이 씀
  - 그림 비율은 4:3, 폭은 감싸는 쪽이 정함
  - 서버 사진('/api/v1/thermal/images/{fileId}')은 로그인이 필요해 useThermalSrc 로 받아서 띄움
  - streamUrl 이 있으면(3단계, thermalStream.js 가 고름) 사진 대신 VMS 영상(mp4)을 반복 재생
    · 영상이 뜨기 전에는 사진을 포스터로 보여 줌
    · 영상 오류(404 등)나 STREAM_TIMEOUT_MS 안에 첫 장면이 안 오면(VMS 방화벽 막힘 등) 사진으로 되돌림
-->
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import colorBar from '@/assets/images/thermal/ir_bar.png'
import { useThermalSrc } from './useThermalSrc'

/** 영상 첫 장면을 기다리는 시간 (넘으면 사진으로) */
const STREAM_TIMEOUT_MS = 7000

const props = defineProps({
  imageUrl: { type: String, required: true },
  /** 실시간 영상 주소 (없으면 사진만) */
  streamUrl: { type: String, default: null },
  /** 최고 온도 (없으면 표시 안 함) */
  max: { type: Number, default: null },
  /** 최고 지점 { x, y } (그림 안 비율) */
  maxPoint: { type: Object, default: null },
  maxLabel: { type: String, default: '최고' },
  /** 값 말풍선 위치 (지점 기준 px, 설계안 mlx·mly) */
  labelOffset: { type: Object, default: () => ({ x: -150, y: 30 }) },
  /** 색 눈금 범위 { min, max } */
  scale: { type: Object, default: () => ({ min: 20, max: 120 }) },
  /** true: 실시간(LIVE), false: 저장된 한 장(SNAPSHOT) */
  live: { type: Boolean, default: true },
  zoneName: { type: String, default: '' },
  /** 왼쪽 아래 촬영 시각 (저장된 한 장일 때) */
  time: { type: String, default: '' },
})

// <img> 에 넣을 주소 (서버 사진이면 blob:, 예시 그림이면 그대로)
const src = useThermalSrc(() => props.imageUrl)

// ── 실시간 영상 ──
// 영상이 실패했는지 (true 면 사진으로). 주소 글자가 바뀔 때만 다시 시도함
// (설비 화면이 열화상을 다시 받아도 같은 주소면 죽은 영상을 또 부르지 않음)
const streamFailed = ref(false)
let timer = null
const clearTimer = () => {
  clearTimeout(timer)
  timer = null
}

watch(
  () => props.streamUrl,
  (url) => {
    streamFailed.value = false
    clearTimer()
    if (!url) return
    // 주소에 닿지 못하면 error 없이 오래 멈춰 있을 수 있어서 시간 제한을 둠
    timer = setTimeout(() => (streamFailed.value = true), STREAM_TIMEOUT_MS)
  },
  { immediate: true },
)

const showVideo = computed(() => !!props.streamUrl && !streamFailed.value)

/** 첫 장면 도착 → 시간 제한 끔 */
const onVideoLoaded = () => clearTimer()
/** 영상 오류(404 · 연결 실패 등) → 사진으로 */
function onVideoError() {
  clearTimer()
  streamFailed.value = true
}

onBeforeUnmount(clearTimer)

const point = computed(() => (props.maxPoint ? { left: `${props.maxPoint.x * 100}%`, top: `${props.maxPoint.y * 100}%` } : null))
</script>

<template>
  <div class="ir">
    <!-- 3단계: VMS 영상 (자동 재생은 muted 가 꼭 있어야 함, 오류 · 시간 초과면 아래 사진으로) -->
    <video
      v-if="showVideo"
      class="frame"
      :class="{ live }"
      :src="streamUrl"
      :poster="src ?? undefined"
      autoplay
      muted
      loop
      playsinline
      aria-label="열화상 카메라 실시간 영상"
      @loadeddata="onVideoLoaded"
      @error="onVideoError"
    />
    <img v-else-if="src" class="frame" :class="{ live }" :src="src" alt="열화상 카메라 장면" />
    <!--
      사진 위 표시(LIVE · 구역 이름 · 색 눈금 · 최고 지점 · 시각)는 사진일 때만.
      VMS 영상에는 LIVE · 카메라 이름 · 색 눈금 · 최고 지점이 이미 그려져 있어 겹치고,
      최고 지점은 사진 한 장 기준이라 움직이는 영상과 맞지 않음
    -->
    <template v-if="!showVideo">
      <div class="tl">
        <span class="tag"><template v-if="live"><i />LIVE</template><template v-else>SNAPSHOT</template></span>
        <span v-if="zoneName" class="tag name">{{ zoneName }}</span>
      </div>
      <div class="bar"><img :src="colorBar" alt="" /></div>
      <span class="bt top">{{ scale.max }}℃</span>
      <span class="bt bottom">{{ scale.min }}℃</span>
      <template v-if="point && max != null">
        <div class="mk" :style="point" />
        <div class="ml" :style="{ left: `calc(${point.left} + ${labelOffset.x}px)`, top: `calc(${point.top} + ${labelOffset.y}px)` }">
          <small>{{ maxLabel }}</small>{{ max }} ℃
        </div>
      </template>
      <span v-if="time" class="tag bl mono">{{ time }}</span>
    </template>
  </div>
</template>

<style scoped>
.ir {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: #0b0720;
}

.frame {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 영상은 화면 안에 글자(카메라 이름 · 눈금)가 그려져 있어 잘리지 않게 전체를 보여 줌
   (본사 화재 상세처럼 넓은 칸이면 양옆이 어두운 바탕으로 남음) */
video.frame {
  object-fit: contain;
}

/* 실시간일 때 밝기가 살짝 일렁임 (설계안 irflick 3초) */
.frame.live {
  animation: ir-flick 3s ease-in-out infinite;
}

.tl {
  position: absolute;
  left: 10px;
  top: 10px;
  display: flex;
  gap: 6px;
}

.tag {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: rgba(2, 6, 23, 0.62);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.tag.name {
  font-family: var(--font-sans);
}

.tag i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ef4444;
  animation: ir-blink 1.2s steps(2, jump-none) infinite;
}

.tag.bl {
  position: absolute;
  left: 10px;
  bottom: 10px;
}

.bar {
  position: absolute;
  right: 10px;
  top: 36px;
  bottom: 36px;
  width: 10px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 5px;
  overflow: hidden;
}

.bar img {
  display: block;
  width: 100%;
  height: 100%;
}

.bt {
  position: absolute;
  right: 26px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: #fff;
  text-shadow: 0 1px 2px #000;
}

.bt.top {
  top: 34px;
}

.bt.bottom {
  bottom: 34px;
}

/* 최고 온도 지점: 흰 네모 + 위아래 짧은 선 */
.mk {
  position: absolute;
  width: 44px;
  height: 44px;
  margin: -22px 0 0 -22px;
  border: 2px solid #fff;
  border-radius: var(--radius-sm);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.5);
  animation: ir-mark 1.4s ease-in-out infinite;
}

.mk::before,
.mk::after {
  content: '';
  position: absolute;
  left: 50%;
  width: 2px;
  height: 8px;
  margin-left: -1px;
  background: #fff;
}

.mk::before {
  top: -8px;
}

.mk::after {
  bottom: -8px;
}

.ml {
  position: absolute;
  padding: 3px 9px;
  border-radius: 7px;
  background: #fff;
  color: var(--color-text);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}

.ml small {
  margin-right: 5px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-sub);
}

@keyframes ir-blink {
  50% {
    opacity: 0;
  }
}

@keyframes ir-mark {
  0%,
  100% {
    box-shadow:
      0 0 0 1px rgba(0, 0, 0, 0.5),
      0 0 0 0 rgba(255, 255, 255, 0.6);
  }
  50% {
    box-shadow:
      0 0 0 1px rgba(0, 0, 0, 0.5),
      0 0 0 8px rgba(255, 255, 255, 0);
  }
}

@keyframes ir-flick {
  0%,
  100% {
    filter: brightness(1);
  }
  50% {
    filter: brightness(1.06) saturate(1.08);
  }
}

/* 움직임 줄이기: 반복 움직임 멈춤 */
@media (prefers-reduced-motion: reduce) {
  .frame.live,
  .tag i,
  .mk {
    animation: none;
  }
}
</style>
