<!--
  점검 일정 달력 (화면설계안 vInspect() #cal)
  - 이번 달 날짜마다 완료(초록) · 예정(파랑) · 지연(빨강) 점, 오늘은 파란 칸
  - 점이 있는 날을 누르면 그날 점검 목록이 작은 창으로 뜸 (바깥을 누르면 닫힘)
-->
<script setup>
import { computed, ref } from 'vue'
import { SCHEDULE_STATE, ymd } from './inspectionMeta'
import { getDaySchedule } from '@/api/inspections'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  /** 'YYYY-MM' */
  month: { type: String, default: '' },
  /** summary.calendar [{ date, done, planned, delayed }] */
  calendar: { type: Array, default: () => [] },
  /** 그날 목록을 받을 공장 (공장 담당자는 자기 공장, 없으면 담당 범위 전체) */
  factoryId: { type: Number, default: null },
})

const today = ymd(new Date())
const WEEK = ['일', '월', '화', '수', '목', '금', '토']

/** 달력 칸: 앞쪽 빈칸 + 날짜 + 뒤쪽 다음 달 날짜 (설계안처럼 흐리게) */
const cells = computed(() => {
  if (!props.month) return []
  const [y, m] = props.month.split('-').map(Number)
  const first = new Date(y, m - 1, 1).getDay()
  const last = new Date(y, m, 0).getDate()
  const byDate = Object.fromEntries(props.calendar.map((c) => [c.date, c]))
  const list = Array.from({ length: first }, (_, i) => ({ key: `b${i}`, blank: true }))
  for (let d = 1; d <= last; d += 1) {
    const date = `${props.month}-${String(d).padStart(2, '0')}`
    const c = byDate[date]
    const dots = c ? [...Array(c.done).fill('DONE'), ...Array(c.planned).fill('PLANNED'), ...Array(c.delayed).fill('DELAYED')] : []
    list.push({ key: date, d, date, dots, today: date === today })
  }
  const tail = (7 - (list.length % 7)) % 7
  for (let d = 1; d <= tail; d += 1) list.push({ key: `n${d}`, d, muted: true })
  return list
})

/* ───────── 그날 점검 목록 ───────── */
const pop = ref(null) // { date, items, loading, error, left, top }
async function openDay(cell, e) {
  if (!cell.dots?.length) return
  const r = e.currentTarget
  pop.value = { date: cell.date, items: [], loading: true, error: '', left: Math.min(r.offsetLeft, 100), top: r.offsetTop + r.offsetHeight + 4 }
  let items = []
  let error = ''
  try {
    items = await getDaySchedule(cell.date, props.factoryId ?? undefined)
  } catch (err) {
    error = err.message
  }
  // 그사이 다른 날을 눌렀으면 늦게 온 응답은 버림
  if (pop.value?.date !== cell.date) return
  Object.assign(pop.value, { items, loading: false, error })
}
</script>

<template>
  <BaseCard title="점검 일정">
    <div class="calh">
      {{ month ? `${month.slice(0, 4)}년 ${Number(month.slice(5))}월` : '' }}
      <span class="lgs">
        <span v-for="(s, k) in SCHEDULE_STATE" :key="k"><i :style="{ background: s.color }" />{{ s.label }}</span>
      </span>
    </div>
    <div class="cal">
      <div class="w"><span v-for="w in WEEK" :key="w">{{ w }}</span></div>
      <div class="d">
        <template v-for="c in cells" :key="c.key">
          <span v-if="c.blank" />
          <span v-else-if="c.muted" class="mu mono">{{ c.d }}</span>
          <button
            v-else
            type="button"
            class="day mono"
            :class="{ td: c.today, lk: c.dots.length, open: pop?.date === c.date }"
            :aria-label="`${c.d}일 점검 ${c.dots.length}건`"
            @click="openDay(c, $event)"
          >
            {{ c.d }}
            <em v-if="c.dots.length"><i v-for="(s, i) in c.dots" :key="i" :style="{ background: SCHEDULE_STATE[s].color }" /></em>
          </button>
        </template>
      </div>

      <!-- 그날 점검 목록 -->
      <template v-if="pop">
        <div class="backdrop" @click="pop = null" />
        <div class="pop pop-in" :style="{ left: `${pop.left}px`, top: `${pop.top}px` }" role="dialog" :aria-label="`${pop.date} 점검`">
          <b class="ph">{{ Number(pop.date.slice(5, 7)) }}월 {{ Number(pop.date.slice(8)) }}일 점검 {{ pop.items.length || '' }}{{ pop.items.length ? '건' : '' }}</b>
          <div v-for="it in pop.items" :key="it.scheduleId" class="pi">
            <i :style="{ background: SCHEDULE_STATE[it.status].color }" />
            <!-- 시각 · 공정 · 담당자 (서버가 비워 준 칸은 빼고 보여 줌) -->
            <span class="pt">{{ it.title }}<small>{{ [it.time, it.processName, it.assignee].filter(Boolean).join(' · ') }}</small></span>
            <span class="m-chip" :class="SCHEDULE_STATE[it.status].chip">{{ SCHEDULE_STATE[it.status].label }}</span>
          </div>
          <p v-if="pop.loading" class="wait">불러오는 중</p>
          <p v-else-if="pop.error" class="wait">{{ pop.error }}</p>
          <p v-else-if="!pop.items.length" class="wait">점검 일정이 없습니다.</p>
        </div>
      </template>
    </div>
  </BaseCard>
</template>

<style scoped>
.calh {
  display: flex;
  align-items: center;
  padding: 12px 20px 8px;
  font-size: 14.5px;
  font-weight: 700;
}

.lgs {
  margin-left: auto;
  display: flex;
  gap: 12px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-sub);
}

.lgs i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
}

.cal {
  position: relative;
  padding: 0 14px 14px;
}

.w,
.d {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
}

.w span {
  height: 24px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.d > span,
.day {
  position: relative;
  z-index: 0;
  height: 35px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 4px 0 0;
  border: none;
  border-radius: 5px;
  background: none;
  font-size: 13px;
  color: #334155;
}

.day {
  cursor: default;
  transition: background 120ms;
}

.day.lk {
  cursor: pointer;
}

.day.lk:hover,
.day.open {
  background: #f1f5f9;
}

.mu {
  color: #cbd5e1 !important;
}

/* 오늘: 파란 칸 + 흰 글자 */
.day.td {
  font-weight: 600;
  color: #fff;
}

.day.td::before {
  content: '';
  position: absolute;
  top: 1px;
  left: 50%;
  z-index: -1;
  width: 26px;
  height: 23px;
  transform: translateX(-50%);
  border-radius: 5px;
  background: var(--color-primary);
}

.day em {
  position: absolute;
  bottom: 4px;
  left: 50%;
  display: flex;
  gap: 2px;
  transform: translateX(-50%);
}

.day em i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.backdrop {
  position: fixed;
  inset: 0;
  z-index: 3;
}

.pop {
  position: absolute;
  z-index: 4;
  width: 300px;
  padding-bottom: 4px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.ph {
  display: block;
  padding: 10px 12px 4px;
  font-size: 13px;
}

.pi {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-top: 1px solid #f1f5f9;
  font-size: 13.5px;
}

.pi > i {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
}

.pt {
  flex: 1;
}

.pt small {
  display: block;
  font-size: 12px;
  color: var(--color-text-weak);
}

.wait {
  padding: 8px 12px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}
</style>
