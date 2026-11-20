<!--
  '이벤트 이력' 카드 (설계안 vEquip() #lg2): 오늘 이벤트를 최근 순으로
  - 위쪽 구분 버튼으로 걸러 보기 (전체 / 알람 / 조치 / 기준값). 바꿀 때마다 줄이 다시 차례로 나타남
  - 아래: 이전 이력은 통계·이력 관리 화면으로
  - 기준값 조정 줄의 '기준값 관리에서 보기' → threshold 이벤트 (화면이 기준값 관리 창을 엶)
-->
<script setup>
import { computed, ref } from 'vue'
import EventRow from './EventRow.vue'
import { EVENT_FILTERS } from './eventTypes'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import SegButtons from '@/components/SegButtons.vue'

const props = defineProps({
  /** GET /equipment/{id}/events 응답 */
  events: { type: Array, required: true },
  historyTo: { type: Object, required: true },
})

const emit = defineEmits(['threshold'])

const filter = ref('ALL')
const rows = computed(() => (filter.value === 'ALL' ? props.events : props.events.filter((e) => e.type === filter.value)))
</script>

<template>
  <BaseCard title="이벤트 이력" sub="오늘">
    <template #actions>
      <SegButtons v-model="filter" :options="EVENT_FILTERS" small />
    </template>

    <div class="body">
      <!-- key 를 걸러 보기 값으로 → 바꿀 때마다 줄이 새로 그려지며 다시 나타남 -->
      <div v-if="rows.length" :key="filter" class="list">
        <EventRow v-for="(e, i) in rows" :key="e.at + e.text" :event="e" :index="i" @threshold="emit('threshold')" />
      </div>
      <div v-else class="empty">
        <AppIcon name="clock" :size="24" />
        <b>{{ filter === 'ALL' ? '오늘 기록된 이벤트가 없습니다' : '이 종류의 이벤트가 없습니다' }}</b>
      </div>

      <RouterLink :to="historyTo" class="more">이전 이력은 통계·이력 관리에서<AppIcon name="cr" :size="13" /></RouterLink>
    </div>
  </BaseCard>
</template>

<style scoped>
.body {
  height: 438px;
  display: flex;
  flex-direction: column;
  padding: 18px 20px 14px;
}

.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--color-text-weak);
}

.empty b {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.more {
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--color-text-sub);
  text-decoration: none;
}

.more:hover {
  color: var(--color-primary);
}
</style>
