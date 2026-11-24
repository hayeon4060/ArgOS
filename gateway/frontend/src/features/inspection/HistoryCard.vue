<!--
  점검 이력 카드 (개편안 A안 오른쪽)
  - 목록 = 스케줄의 점검 건. 최신순, 오늘 점검(예정 포함)이 맨 위 (부모의 useInspectionSchedule 이 정렬)
  - 제목줄 오른쪽: '점검일지 작성' · '월별 보고서'
  - 거르기 줄: 구분(전체/수기/전자) · 결과(전체/불량만) · 기간(1일/7일/30일, 수정안 4) · 찾기(설비 · 점검자)
    기간은 부모가 다시 불러 오고, 나머지는 받은 목록 안에서 거름
-->
<script setup>
import { computed, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import SegButtons from '@/components/SegButtons.vue'
import HistoryTable from './HistoryTable.vue'
import { PERIODS } from './inspectionMeta'

const props = defineProps({
  items: { type: Array, required: true },
  today: { type: String, required: true },
  loading: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false },
})

const days = defineModel('days', { type: Number, required: true })

defineEmits(['open', 'scan', 'download', 'write', 'report'])

const source = ref('ALL')
const result = ref('ALL')
const q = ref('')

const count = (s) => props.items.filter((it) => it.source === s).length
const sourceOptions = computed(() => [
  { value: 'ALL', label: `전체 ${props.items.length}` },
  { value: 'PAPER', label: `수기 ${count('PAPER')}` },
  { value: 'DIGITAL', label: `전자 ${count('DIGITAL')}` },
])
const RESULT_OPTIONS = [
  { value: 'ALL', label: '결과 전체' },
  { value: 'NG', label: '불량만' },
]

const rows = computed(() => {
  const word = q.value.trim()
  return props.items.filter(
    (it) =>
      (source.value === 'ALL' || it.source === source.value) &&
      (result.value === 'ALL' || it.result === result.value) &&
      (!word || it.equipmentName.includes(word) || it.assignee.includes(word)),
  )
})
</script>

<template>
  <BaseCard class="hist" title="점검 이력" :sub="`${rows.length}건`">
    <template #actions>
      <button v-if="!readOnly" type="button" class="btn btn-sm" @click="$emit('write')"><AppIcon name="edit" :size="14" />점검일지 작성</button>
      <button type="button" class="btn btn-sm btn-primary" @click="$emit('report')"><AppIcon name="file" :size="14" />월별 보고서</button>
    </template>

    <div class="tools">
      <SegButtons v-model="source" :options="sourceOptions" small />
      <SegButtons v-model="result" :options="RESULT_OPTIONS" small />
      <span class="lbl">기간</span>
      <SegButtons v-model="days" :options="PERIODS" small />
      <span class="gap" />
      <label class="srch">
        <AppIcon name="search" :size="14" />
        <input v-model="q" type="search" placeholder="설비 · 점검자 찾기" aria-label="설비 · 점검자 찾기" />
      </label>
    </div>

    <div class="scroll">
      <HistoryTable :rows="rows" :today="today" @open="(it) => $emit('open', it)" @scan="(it) => $emit('scan', it)" @download="(it) => $emit('download', it)" />
      <div v-if="!rows.length" class="empty">{{ loading ? '불러오는 중…' : '해당하는 점검이 없습니다.' }}</div>
    </div>
  </BaseCard>
</template>

<style scoped>
.hist {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.btn-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.tools {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.lbl {
  margin-left: 4px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.gap {
  flex: 1;
}

.srch {
  height: 32px;
  width: 190px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  color: var(--color-text-weak);
}

.srch:focus-within {
  border-color: var(--color-primary);
}

.srch input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: none;
  font-size: 13px;
  color: var(--color-text);
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.empty {
  padding: 36px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-weak);
}
</style>
