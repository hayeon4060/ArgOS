<!--
  사업장 카드: 제목줄(범례) + 남한 지도(KoreaMap)
  - 제목 '사업장' = 대시보드 단계 경로의 첫 단계 (사업장 › 울산공장 › 5공장 › 도장 공정, 2026-11-18)
  - 핀을 누르면 select 이벤트로 사업장 ID 를 알림
-->
<script setup>
import KoreaMap from './KoreaMap.vue'
import { STAGES } from './stages'
import BaseCard from '@/components/BaseCard.vue'

defineProps({
  sites: { type: Array, required: true },
  /** 강조할 사업장 ID */
  currentId: { type: Number, default: null },
})

const emit = defineEmits(['select'])
</script>

<template>
  <BaseCard title="사업장" sub="사업장을 누르면 이동">
    <template #actions>
      <span v-for="s in STAGES" :key="s.level" class="legend">
        <i :style="{ borderColor: s.color }" />{{ s.label }}
      </span>
    </template>
    <KoreaMap :sites="sites" :current-id="currentId" @select="emit('select', $event)" />
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
  border: 2.5px solid;
  border-radius: 3px;
  background: #fff;
}
</style>
