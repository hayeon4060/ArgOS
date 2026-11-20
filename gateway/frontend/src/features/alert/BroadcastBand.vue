<!--
  공장 내 경고 방송 띠 (화면설계안 mFire() — 화재일 때만)
  - 빨간 스피커(1초마다 커졌다 작아짐) + '공장 내 경고 방송 송출 중' + 방송 문구
  - 오른쪽: 소리 막대가 움직임(방송 중에만) + '반복 n회째' (7초마다 1회, 설계안과 같은 계산)
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** { on, message } (GET /fires/{id} 의 broadcast) */
  broadcast: { type: Object, required: true },
  /** 방송 시작 뒤 지난 초 */
  elapsedSec: { type: Number, default: 0 },
})

// 소리 막대 32개: 높이·속도가 조금씩 다르게 (설계안 값)
const BARS = [10, 22, 30, 16, 34, 24, 12, 28, 36, 20, 14, 30, 24, 34, 18, 10, 26, 32, 20, 12, 28, 36, 22, 14, 30, 18, 26, 34, 16, 22, 30, 12]
const bars = BARS.map((h, i) => ({ h, delay: -((i * 137) % 700), dur: 520 + ((i * 53) % 380) }))

const repeat = computed(() => 1 + Math.floor(props.elapsedSec / 7))
</script>

<template>
  <div class="band" role="alert">
    <span class="spk"><AppIcon name="vol" :size="22" /></span>
    <div>
      <b>{{ broadcast.on ? '공장 내 경고 방송 송출 중' : '경고 방송 멈춤' }}</b>
      <p>"{{ broadcast.message }}"</p>
    </div>
    <div v-if="broadcast.on" class="wave" aria-hidden="true">
      <i v-for="(b, i) in bars" :key="i" :style="{ height: `${b.h}px`, animationDelay: `${b.delay}ms`, animationDuration: `${b.dur}ms` }" />
    </div>
    <span v-if="broadcast.on" class="mono rep">반복 {{ repeat }}회째</span>
  </div>
</template>

<style scoped>
.band {
  height: 76px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  border: 1.5px solid var(--color-level-alarm);
  border-radius: var(--radius-md);
  background: #fef2f2;
}

.spk {
  width: 44px;
  height: 44px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-level-alarm);
  color: #fff;
  animation: spk 1s ease-in-out infinite;
}

b {
  font-size: 16px;
  color: var(--color-level-alarm-text);
}

p {
  margin-top: 3px;
  font-size: 13px;
  color: #7f1d1d;
}

.wave {
  margin-left: auto;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 3px;
}

.wave i {
  width: 4px;
  border-radius: 2px;
  background: var(--color-level-alarm);
  transform-origin: center;
  animation: wave 0.7s ease-in-out infinite alternate;
}

.rep {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-level-alarm-text);
  white-space: nowrap;
}

@keyframes spk {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.14);
  }
}

@keyframes wave {
  from {
    transform: scaleY(0.35);
  }
  to {
    transform: scaleY(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spk,
  .wave i {
    animation: none;
  }
}
</style>
