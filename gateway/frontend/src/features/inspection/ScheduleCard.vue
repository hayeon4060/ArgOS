<!--
  점검 스케줄 카드 (개편안 A안 왼쪽 위)
  - 제목줄: 월 이동(이전 · 다음) · 오늘 · 스케줄 추가
  - 월 합계 3칸: 완료 / 예정 / 누락 (수정안 1. 이름은 inspectionMeta.js SCHEDULE_STATE 한 곳)
  - 달력 (CalendarGrid) → 날짜를 누르면 오른쪽에서 그날 점검 목록 패널 (DayPanel)
  - 데이터는 부모(InspectionPage)가 불러 줌. 이 카드는 그리기와 고른 날짜만 가짐
-->
<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import CalendarGrid from './CalendarGrid.vue'
import DayPanel from './DayPanel.vue'
import { SCHEDULE_STATE, STATE_ORDER, countStates, monthName, shiftMonth } from './inspectionMeta'

const props = defineProps({
  /** 'YYYY-MM' */
  month: { type: String, required: true },
  items: { type: Array, required: true },
  today: { type: String, required: true },
  readOnly: { type: Boolean, default: false },
  /** 바깥에서 열어 둘 날짜 (스케줄 추가 뒤 그날 목록을 보여 줌) */
  focusDate: { type: String, default: '' },
})

const emit = defineEmits(['update:month', 'add', 'open'])

const selected = ref('')
const totals = computed(() => countStates(props.items))
const dayItems = computed(() => props.items.filter((it) => it.date === selected.value))

watch(
  () => props.focusDate,
  (d) => d && pick(d, true),
)

/** 날짜 고르기 (같은 날을 다시 누르면 닫음). 다른 달 날짜면 그 달로 넘어감 */
function pick(date, keep = false) {
  selected.value = !keep && selected.value === date ? '' : date
  if (selected.value && !selected.value.startsWith(props.month)) emit('update:month', selected.value.slice(0, 7))
}

function move(n) {
  selected.value = ''
  emit('update:month', shiftMonth(props.month, n))
}

function goToday() {
  selected.value = props.today
  emit('update:month', props.today.slice(0, 7))
}

/** 스케줄 추가 날짜: 고른 날 → (이번 달이면) 오늘 → 그 달 1일 */
function add() {
  emit('add', selected.value || (props.month === props.today.slice(0, 7) ? props.today : `${props.month}-01`))
}
</script>

<template>
  <BaseCard class="cal-card" title="점검 스케줄">
    <template #sub>
      <span class="mnav">
        <button type="button" class="ibtn" aria-label="이전 달" @click="move(-1)"><AppIcon name="cl" :size="15" /></button>
        <b class="mono">{{ monthName(month) }}</b>
        <button type="button" class="ibtn" aria-label="다음 달" @click="move(1)"><AppIcon name="cr" :size="15" /></button>
      </span>
    </template>
    <template #actions>
      <button type="button" class="btn btn-sm" @click="goToday">오늘</button>
      <button v-if="!readOnly" type="button" class="btn btn-sm btn-primary" @click="add"><AppIcon name="plus" :size="14" />스케줄 추가</button>
    </template>

    <div class="msum">
      <div v-for="s in STATE_ORDER" :key="s" :class="SCHEDULE_STATE[s].cls">
        {{ SCHEDULE_STATE[s].label }}<b class="mono">{{ totals[s] }}</b>
      </div>
    </div>

    <CalendarGrid :month="month" :items="items" :today="today" :selected="selected" @pick="pick" />

    <Transition name="slide">
      <DayPanel
        v-if="selected"
        :date="selected"
        :items="dayItems"
        :read-only="readOnly"
        @close="selected = ''"
        @open="(it) => emit('open', it)"
        @add="(d) => emit('add', d)"
      />
    </Transition>
  </BaseCard>
</template>

<style scoped>
.cal-card {
  position: relative; /* 그날 점검 패널 자리 */
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* 제목 옆 회색 설명 칸에 월 이동을 넣음 → 글자색 · 잘림 풀기 */
.cal-card :deep(.sub) {
  overflow: visible;
  color: var(--color-text);
}

.mnav {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 4px;
}

.mnav b {
  min-width: 96px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}

.ibtn {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--color-text-sub);
  cursor: pointer;
  transition: background var(--m-hover);
}

.ibtn:hover {
  background: var(--color-surface-sub);
}

.btn-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.msum {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border);
}

.msum div {
  flex: 1;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
}

.msum b {
  font-size: 20px;
  font-weight: 600;
}

.msum .done {
  background: #f0fdf4;
  color: var(--color-level-normal-text);
}

.msum .plan {
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
}

.msum .miss {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

/* 그날 점검 패널: 오른쪽에서 밀려 나옴 (화면설계서 R2 --m-panel), 닫힐 때는 짧게 */
.slide-enter-active {
  transition: transform var(--m-panel);
}

.slide-leave-active {
  transition: transform var(--m-pop-out);
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(105%);
}
</style>
