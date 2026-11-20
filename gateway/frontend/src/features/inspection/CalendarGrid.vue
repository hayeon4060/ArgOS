<!--
  점검 스케줄 달력 칸 (일 ~ 토, 그 달이 걸친 주만큼)
  - 날짜 칸: 숫자 + 상태별 건수 칩 '완료 2' '예정 1' '누락 1' (StateCounts)
  - 오늘은 파란 테두리 · 파란 숫자, 고른 날은 옅은 파랑 바탕
  - 앞뒤 달 날짜도 흐리게 보이고 누르면 그 달로 넘어감 (부모가 처리)
-->
<script setup>
import { computed } from 'vue'
import StateCounts from './StateCounts.vue'
import { countStates, ymd } from './inspectionMeta'

const props = defineProps({
  /** 'YYYY-MM' */
  month: { type: String, required: true },
  /** 그 달의 점검 건 */
  items: { type: Array, required: true },
  today: { type: String, required: true },
  selected: { type: String, default: '' },
})

defineEmits(['pick'])

const WEEK = ['일', '월', '화', '수', '목', '금', '토']

/** 칸 목록 [{ date, day, out, sun, counts }] */
const cells = computed(() => {
  const y = Number(props.month.slice(0, 4))
  const m = Number(props.month.slice(5, 7)) - 1
  const first = new Date(y, m, 1).getDay()
  const days = new Date(y, m + 1, 0).getDate()
  const weeks = Math.ceil((first + days) / 7)
  // 날짜별로 미리 묶어 둠 (칸마다 전체를 다시 거르지 않게)
  const byDate = {}
  props.items.forEach((it) => (byDate[it.date] ??= []).push(it))
  return Array.from({ length: weeks * 7 }, (_, c) => {
    const d = new Date(y, m, c - first + 1)
    const date = ymd(d)
    return { date, day: d.getDate(), out: d.getMonth() !== m, sun: d.getDay() === 0, counts: countStates(byDate[date] ?? []) }
  })
})

const weeks = computed(() => cells.value.length / 7)
</script>

<template>
  <div class="cal" :style="{ gridTemplateRows: `20px repeat(${weeks}, minmax(0, 1fr))` }">
    <div v-for="w in WEEK" :key="w" class="wd">{{ w }}</div>
    <button
      v-for="c in cells"
      :key="c.date"
      type="button"
      class="d"
      :class="{ out: c.out, sun: c.sun, today: c.date === today, sel: c.date === selected }"
      :aria-label="`${c.date} 점검 보기`"
      @click="$emit('pick', c.date)"
    >
      <span class="n mono">{{ c.day }}</span>
      <StateCounts :counts="c.counts" />
    </button>
  </div>
</template>

<style scoped>
.cal {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  padding: 8px 16px 14px;
}

.wd {
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  color: var(--color-text-weak);
}

.d {
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
  padding: 6px 7px;
  overflow: hidden;
  border: 1px solid #eef2f6;
  border-radius: var(--radius-sm);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--m-hover),
    background var(--m-hover);
}

.d:hover {
  border-color: var(--color-border-strong);
  background: var(--color-surface-sub);
}

.n {
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.sun .n {
  color: var(--color-level-alarm);
  opacity: 0.7;
}

.out {
  opacity: 0.35;
}

.today {
  border-color: var(--color-primary);
}

.today .n {
  align-self: flex-start;
  padding: 0 5px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
  color: #fff;
  font-weight: 600;
  opacity: 1;
}

.sel {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}
</style>
