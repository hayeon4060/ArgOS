<!--
  구분 버튼 묶음 (설계안 css/base.css 의 .seg 모양: 회색 바탕 안에서 고른 칸만 흰색)
  - 사용 예: <SegButtons v-model="range" :options="[{ value: '1h', label: '1시간' }, ...]" />
  - small: 카드 제목줄용 작은 크기 (설계안 .seg.sm)
-->
<script setup>
defineProps({
  /** [{ value, label }] */
  options: { type: Array, required: true },
  small: { type: Boolean, default: false },
})

const model = defineModel({ type: [String, Number], required: true })
</script>

<template>
  <span class="seg" :class="{ small }" role="tablist">
    <button
      v-for="o in options"
      :key="o.value"
      type="button"
      role="tab"
      :aria-selected="model === o.value"
      :class="{ on: model === o.value }"
      @click="model = o.value"
    >
      {{ o.label }}
    </button>
  </span>
</template>

<style scoped>
.seg {
  display: inline-flex;
  padding: 3px;
  border-radius: var(--radius-sm);
  background: #f1f5f9;
}

.seg button {
  padding: 5px 12px;
  border: none;
  border-radius: var(--radius-xs);
  background: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-sub);
  white-space: nowrap;
  cursor: pointer;
  /* 고른 칸이 바뀔 때 바탕·그림자가 부드럽게 (설계안 extra.css .18s) */
  transition:
    background 180ms,
    color 180ms,
    box-shadow 180ms;
}

.seg button:not(.on):hover {
  color: var(--color-text);
}

.seg button.on {
  background: #fff;
  color: var(--color-text);
  font-weight: 700;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.12);
}

.small button {
  padding: 3px 9px;
  font-size: 12px;
}
</style>
