<!--
  전체 공장 상태 카드: 공장마다 전류·온도·CO 칸에 '지금 알림 난 센서 수' (2026-11-18 도넛 → 숫자 칸)
  - 전체 센서 수는 보여 주지 않음. 알림이 없으면 초록 체크
  - 공장을 누르면 select 이벤트 (페이지가 이동 또는 말풍선 처리)
-->
<script setup>
import FactoryStatusItem from './FactoryStatusItem.vue'
import BaseCard from '@/components/BaseCard.vue'
import { LEVEL_META, LEVEL } from '@/types/codes'

// 범례: 칸에 나오는 색은 주의·경보 두 가지 (정상은 체크 아이콘)
const LEGEND = [LEVEL_META[LEVEL.WARN], LEVEL_META[LEVEL.ALARM]]

defineProps({
  factories: { type: Array, required: true },
})

const emit = defineEmits(['select'])
</script>

<template>
  <BaseCard title="전체 공장 상태" sub="공장별 알림 센서 수 · 전류 · 온도 · CO">
    <template #actions>
      <span v-for="l in LEGEND" :key="l.key" class="legend">
        <i :style="{ background: l.color }" />{{ l.label }}
      </span>
    </template>

    <div class="list">
      <FactoryStatusItem
        v-for="factory in factories"
        :key="factory.id"
        :factory="factory"
        @select="emit('select', $event)"
      />
    </div>
  </BaseCard>
</template>

<style scoped>
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
