<!--
  공정별 설비 상태 카드 (5공장 공정 배치도 아래)
  - 2026-11-18 재원님 요청: 구역 현황의 '전체 공장 상태' 카드와 같은 모양으로 바꿈
    · 공정마다 이름 + 상태 배지, 전류·온도·CO 칸 3개
    · 칸에는 '지금 알림이 난 센서 수'만 (알림 없으면 초록 체크). 도넛·센서 종류별 막대·열화상 줄은 삭제
  - 칸 하나하나는 전체 공장 상태와 같은 부품(FactoryStatusItem)을 그대로 씀
    · 공정 데이터도 name·level·levelsByType 를 갖고 있어 공장 자리에 그대로 넣을 수 있음
  - 공정을 누르면 select 이벤트 (페이지가 이동 또는 안내)
-->
<script setup>
import { computed } from 'vue'
import BaseCard from '@/components/BaseCard.vue'
import FactoryStatusItem from '@/features/dashboard/FactoryStatusItem.vue'
import { LEVEL, LEVEL_META } from '@/types/codes'

const props = defineProps({
  factoryName: { type: String, required: true },
  /** 공장 현황의 공정 목록 (name, level, levelsByType, equipmentCount) */
  processes: { type: Array, required: true },
})

const emit = defineEmits(['select'])

// 범례: 칸에 나오는 색은 주의·경보 두 가지 (정상은 체크 아이콘) — 전체 공장 상태와 같음
const LEGEND = [LEVEL_META[LEVEL.WARN], LEVEL_META[LEVEL.ALARM]]

// 공장 전체 설비 수 (제목줄)
const equipmentTotal = computed(() => props.processes.reduce((n, p) => n + (p.equipmentCount ?? 0), 0))
</script>

<template>
  <BaseCard title="공정별 설비 상태" :sub="`${factoryName} · 설비 ${equipmentTotal}대 · 공정별 알림 센서 수`">
    <template #actions>
      <span v-for="l in LEGEND" :key="l.key" class="legend">
        <i :style="{ background: l.color }" />{{ l.label }}
      </span>
    </template>

    <div class="list">
      <!-- factory 자리에 공정을 넣음 (이름·단계·종류별 단계 수만 씀) -->
      <FactoryStatusItem
        v-for="p in processes"
        :key="p.id"
        :factory="p"
        @select="emit('select', $event)"
      />
    </div>
  </BaseCard>
</template>

<style scoped>
/* 범례·목록 간격은 전체 공장 상태 카드(FactoryStatusCard)와 같게 */
.legend {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.list {
  display: flex;
  gap: 12px;
  padding: 20px;
}
</style>
