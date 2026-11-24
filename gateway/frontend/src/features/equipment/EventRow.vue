<!--
  이벤트 이력 한 줄 (설계안 css/base.css .tlg .e: 세로 선 위의 점 + 시각 + 제목·설명 + 오른쪽 배지)
  - 기준값 조정 줄의 '기준값 관리에서 보기' 링크는 2026-11-22 재원님 요청으로 삭제 (기준값은 공장 관리에서)
-->
<script setup>
import { computed } from 'vue'
import { chipOf, dotColor, timeOf } from './eventTypes'

const props = defineProps({
  /** { at, type, level, text, detail, by, status } */
  event: { type: Object, required: true },
  /** 몇 번째 줄인지 (차례로 나타나는 지연) */
  index: { type: Number, default: 0 },
})

const chip = computed(() => chipOf(props.event))
</script>

<template>
  <div class="e" :style="{ '--j': Math.min(index, 24) }">
    <span class="d" :style="{ color: dotColor(event) }" />
    <span class="tm mono">{{ timeOf(event.at) }}</span>
    <div class="tx">
      {{ event.text }}
      <small>{{ event.detail }}</small>
    </div>
    <span class="chip" :class="chip.cls">{{ chip.label }}</span>
  </div>
</template>

<style scoped>
.e {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 14px;
  /* 차례로 오른쪽에서 밀려 들어옴 (설계안 extra.css slidein .4s, 줄마다 55ms) */
  animation: ev-slide 400ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
  animation-delay: calc(var(--j, 0) * 55ms);
}

/* 점끼리 잇는 세로 선 */
.e::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: -2px;
  width: 2px;
  background: var(--color-border);
}

.e:last-child::before {
  display: none;
}

.d {
  position: relative;
  z-index: 1;
  flex: none;
  width: 12px;
  height: 12px;
  margin-top: 4px;
  border: 2.5px solid #fff;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 1.5px currentColor;
}

.tm {
  flex: none;
  width: 62px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.tx {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 19px;
}

.tx small {
  display: block;
  font-size: 12px;
  color: var(--color-text-sub);
}

/* 배지 (설계안 .chip) */
.chip {
  flex: none;
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.chip.warn {
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
}

.chip.alarm {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.chip.pri {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.chip.gray {
  background: #f1f5f9;
  color: var(--color-text-sub);
}

.chip.ok {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

@keyframes ev-slide {
  from {
    opacity: 0;
    transform: translateX(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .e {
    animation-name: softin; /* base.css: 투명도만 */
    animation-delay: 0s;
  }
}
</style>
