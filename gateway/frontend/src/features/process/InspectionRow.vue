<!--
  오늘 설비 점검 한 줄: 완료 표시 · 시각 · 설비와 점검 내용 · 담당자 · 상태 칩
  - 상태: DONE 완료(초록) / PLANNED 예정(회색) / DELAYED 지연(빨강)
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  /** GET /inspections/today 의 items 하나 */
  item: { type: Object, required: true },
})

const STATUS = {
  DONE: { label: '완료', cls: 'done' },
  PLANNED: { label: '예정', cls: 'planned' },
  DELAYED: { label: '지연', cls: 'delayed' },
}
</script>

<template>
  <div class="row" :class="STATUS[item.status]?.cls">
    <span class="box"><AppIcon v-if="item.status === 'DONE'" name="ok" :size="12" /></span>
    <span class="mono time">{{ item.time }}</span>
    <div class="text">
      <b>{{ item.equipmentName }}</b>
      <small>{{ item.title }}</small>
    </div>
    <span class="who">{{ item.assignee }}</span>
    <span class="chip">{{ STATUS[item.status]?.label ?? item.status }}</span>
  </div>
</template>

<style scoped>
.row {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.row:last-child {
  border-bottom: none;
}

.row.done {
  cursor: pointer; /* 끝난 점검: 누르면 '점검 관리' 안내 */
}

.row.done:hover {
  background: var(--color-surface-sub);
}

.row.delayed {
  background: #fef6f6;
}

/* 완료 체크 상자 */
.box {
  width: 18px;
  height: 18px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--color-border-strong);
  border-radius: 4px;
  color: #fff;
}

.done .box {
  background: var(--color-level-normal);
  border-color: var(--color-level-normal);
}

.box :deep(.app-icon) {
  stroke-width: 3.5;
}

.time {
  width: 42px;
  color: var(--color-text-sub);
}

.text {
  flex: 1;
  min-width: 0;
}

.text b {
  display: block;
  font-size: 14px;
}

.text small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-weak);
}

.who {
  color: var(--color-text-sub);
  white-space: nowrap;
}

.chip {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  background: #f1f5f9;
  color: var(--color-text-sub);
}

.done .chip {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

.delayed .chip {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}
</style>
