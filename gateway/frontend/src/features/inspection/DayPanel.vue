<!--
  그날 점검 목록 패널 (달력 카드 오른쪽에서 밀려 나옴)
  - 한 줄: 상태 · 설비 · 서류 양식 · 점검자 (불량이면 빨간 '불량') · 시각
  - 줄을 누르면 open(점검 건) → 완료 건은 양식 보기, 예정 · 누락 건은 양식 작성
  - 아래 '이 날 스케줄 추가'
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import InspBadge from './InspBadge.vue'
import StateCounts from './StateCounts.vue'
import { SCHEDULE_STATE, countStates, toDate } from './inspectionMeta'

const props = defineProps({
  date: { type: String, required: true },
  /** 그날 점검 건 */
  items: { type: Array, required: true },
  readOnly: { type: Boolean, default: false },
})

defineEmits(['close', 'open', 'add'])

const title = computed(() => {
  const d = toDate(props.date)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${'일월화수목금토'[d.getDay()]})`
})
const list = computed(() => [...props.items].sort((a, b) => a.time.localeCompare(b.time)))
</script>

<template>
  <aside class="day" aria-label="그날 점검 목록">
    <div class="dh">
      {{ title }}
      <StateCounts :counts="countStates(items)" />
      <button type="button" class="x" aria-label="닫기" @click="$emit('close')"><AppIcon name="x" :size="16" /></button>
    </div>
    <div class="scroll">
      <button v-for="it in list" :key="it.scheduleId" type="button" class="it" @click="$emit('open', it)">
        <InspBadge :tone="SCHEDULE_STATE[it.status].cls">{{ SCHEDULE_STATE[it.status].label }}</InspBadge>
        <span class="mid">
          <span class="t1">{{ it.equipmentName }}</span>
          <span class="t2">
            <span class="fc mono">{{ it.formType }}</span> · {{ it.assignee }}<template v-if="it.result === 'NG'"> · <b class="ng">불량</b></template>
          </span>
        </span>
        <span class="tm mono">{{ it.time }}</span>
      </button>
      <div v-if="!list.length" class="empty">점검이 없는 날입니다.</div>
    </div>
    <div v-if="!readOnly" class="df">
      <button type="button" class="btn btn-sm" @click="$emit('add', date)"><AppIcon name="plus" :size="14" />이 날 스케줄 추가</button>
    </div>
  </aside>
</template>

<style scoped>
.day {
  position: absolute;
  top: 48px;
  right: 0;
  bottom: 0;
  z-index: 3;
  width: min(330px, 100%);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid var(--color-border-card);
  box-shadow: -10px 0 24px rgba(15, 23, 42, 0.1);
}

.dh {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 8px 10px 16px;
  border-bottom: 1px solid var(--color-border);
  font-size: 15px;
  font-weight: 700;
}

.x {
  margin-left: auto;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-text-sub);
  cursor: pointer;
}

.x:hover {
  background: var(--color-surface-sub);
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.it {
  width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border: none;
  border-bottom: 1px solid #f1f5f9;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.it:hover {
  background: var(--color-surface-sub);
}

.mid {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.t1 {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.t2 {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.fc {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-primary);
}

.ng {
  color: var(--color-level-alarm-text);
}

.tm {
  font-size: 12px;
  color: var(--color-text-sub);
}

.empty {
  padding: 36px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-weak);
}

.df {
  flex: none;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
}

.df .btn {
  width: 100%;
}

.btn-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}
</style>
