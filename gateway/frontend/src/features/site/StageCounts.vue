<!--
  센서 단계 개수 (정상 112 · 주의 0 · 경보 0)
  - 주의·경보 개수가 0보다 크면 숫자를 상태 색으로 표시
  - vertical: 위아래 목록 모양 (도넛 카드 안), 기본은 가로 한 줄
  - size: 숫자 크기 'sm'(14px) / 'lg'(16px)
-->
<script setup>
import { STAGES } from './stages'
import { LEVEL } from '@/types/codes'

defineProps({
  /** { NORMAL, WARN, ALARM } */
  counts: { type: Object, required: true },
  vertical: { type: Boolean, default: false },
  size: { type: String, default: 'sm' },
})
</script>

<template>
  <span class="stage-counts" :class="[size, { vertical }]">
    <span
      v-for="s in STAGES"
      :key="s.level"
      class="item"
      :class="{ [`is-${s.key}`]: s.level !== LEVEL.NORMAL && counts[s.level] > 0 }"
    >
      <i :style="{ background: s.color }" />{{ s.label }}
      <b class="mono">{{ counts[s.level] }}</b>
    </span>
  </span>
</template>

<style scoped>
.stage-counts {
  display: flex;
  gap: 10px;
  font-size: 12.5px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.lg {
  gap: 14px;
}

.item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

b {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.lg b {
  font-size: 16px;
}

/* 위아래 목록: 숫자는 오른쪽 끝 */
.vertical {
  flex-direction: column;
  gap: 5px;
}

.vertical .item {
  display: flex;
  gap: 6px;
}

.vertical b {
  margin-left: auto;
  font-size: 13px;
}

/* .item 까지 붙여야 함: 부모 카드에 is-warn 이 붙어 있으면 모든 숫자가 주황이 되는 문제 방지 */
.item.is-warn b {
  color: var(--color-level-warn-text);
}

.item.is-alarm b {
  color: var(--color-level-alarm-text);
}
</style>
