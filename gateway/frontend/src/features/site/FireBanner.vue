<!--
  사업장 선택 화면 맨 위 화재 띠 (화면설계안 vRegion() .banner)
  - 빨간 띠: 불꽃 + '화재 발생' + 위치 + 오른쪽 '16:58:04 감지 · 경과 00:01:23'
  - 0.9초마다 진해졌다 밝아짐 (설계안 headDng)
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  /** GET /fires/{id} */
  fire: { type: Object, required: true },
  elapsed: { type: String, default: '' },
})
</script>

<template>
  <div class="banner" role="alert">
    <AppIcon name="flame" :size="22" />화재 발생
    <span class="where">{{ fire.location }}</span>
    <span class="mono time">{{ fire.detectedAt.slice(11, 19) }} 감지 · 경과 {{ elapsed }}</span>
  </div>
</template>

<style scoped>
.banner {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-radius: var(--radius-md);
  background: var(--color-level-alarm);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  animation:
    pop-in 240ms ease-out both,
    head-dng 0.9s ease-in-out infinite;
}

.where {
  font-weight: 500;
  opacity: 0.95;
}

.time {
  margin-left: auto;
  font-size: 14px;
  font-weight: 500;
}

@keyframes head-dng {
  0%,
  100% {
    filter: brightness(1);
  }
  50% {
    filter: brightness(0.82);
  }
}

@media (prefers-reduced-motion: reduce) {
  .banner {
    animation: none;
  }
}
</style>
