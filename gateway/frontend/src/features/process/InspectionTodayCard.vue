<!--
  오늘 설비 점검 카드 (설계안 rightCol 'proc' 의 '오늘 설비 점검')
  - 제목줄: 공정 · N건, 오른쪽에 완료 수/전체 수와 '점검 관리' 바로가기
  - 데이터: GET /inspections/today?processId=
-->
<script setup>
import { useRouter } from 'vue-router'
import InspectionRow from './InspectionRow.vue'
import BaseCard from '@/components/BaseCard.vue'
import { toast } from '@/utils/toast'

const props = defineProps({
  processName: { type: String, required: true },
  /** { doneCount, totalCount, items } */
  inspections: { type: Object, required: true },
  /** 점검 관리 화면 주소 (RouterLink to) */
  inspectionRoute: { type: Object, required: true },
})

const router = useRouter()

/** 끝난 점검을 누르면 안내 + '점검 관리' 로 가는 단추 (화면설계안 v-rev.js .tdl .td) */
function onRow(item) {
  if (item.status !== 'DONE') return
  toast(`${item.equipmentName} 점검은 ${item.time}에 끝났습니다. 점검 관리에서 기록을 볼 수 있습니다.`, {
    type: 'info',
    action: '점검 관리',
    onAction: () => router.push(props.inspectionRoute),
  })
}
</script>

<template>
  <BaseCard title="금일 설비 점검" :sub="`${processName} · ${inspections.totalCount}건`">
    <template #actions>
      완료 <b class="mono">{{ inspections.doneCount }}/{{ inspections.totalCount }}</b>
      <RouterLink :to="inspectionRoute" class="link">점검 관리</RouterLink>
    </template>

    <div class="list">
      <InspectionRow v-for="item in inspections.items" :key="item.scheduleId" :item="item" @click="onRow(item)" />
      <p v-if="inspections.items.length === 0" class="empty">금일 점검 일정이 없습니다.</p>
    </div>
  </BaseCard>
</template>

<style scoped>
b {
  font-size: 15px;
  color: var(--color-text);
}

.link {
  color: var(--color-primary);
  font-weight: 600;
  text-decoration: none;
}

.list {
  max-height: 276px;
  overflow-y: auto;
}

.empty {
  padding: 32px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
