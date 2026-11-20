<!--
  공정 지도 카드: 단계 경로 + 범례 + 3D 설비 배치도 + 아래 설비 줄
  - 경로: 사업장 › 울산공장 › 5공장 › 도장 공정 (앞 단계는 누르면 이동, CardCrumb)
    · 2026-11-18 제목 '구역 현황' + '도장 공정 3D 설비 배치도' 경로 → 단계 경로 하나로
  - 설비를 누르면 select-equipment (배기팬을 눌러도 그 설비)
    · 2026-11-19 재원님 요청: 배기팬을 눌러 회전수·켜기/끄기를 바꾸던 창(FanPopover)과 안내 글자 삭제
-->
<script setup>
import EquipmentStrip from './EquipmentStrip.vue'
import PaintMap from './PaintMap.vue'
import BaseCard from '@/components/BaseCard.vue'
import CardCrumb from '@/components/CardCrumb.vue'
import { STAGES } from '@/features/site/stages'

defineProps({
  /** 경로('사업장' 다음부터): [{ label, to }] — to 가 있으면 링크 */
  crumbs: { type: Array, required: true },
  /** 화면용 설비 목록 */
  equipment: { type: Array, required: true },
  /** 공장 관리에서 저장한 설비 위치 { 'top-oven': { x, y } } (DB pos_x · pos_y) */
  positions: { type: Object, default: () => ({}) },
  /** 사용 안 함 설비 (흐리게) */
  inactive: { type: Array, default: () => [] },
  /** 숨길 설비 그림 (등록되지 않은 설비 — 공장 관리에서 지운 설비, processEquipment.js layoutPlacement) */
  hidden: { type: Array, default: () => [] },
})

const emit = defineEmits(['select-equipment'])
</script>

<template>
  <BaseCard>
    <template #title><CardCrumb :items="crumbs" /></template>

    <template #actions>
      <span v-for="s in STAGES" :key="s.level" class="legend"><i :style="{ borderColor: s.color }" />{{ s.label }}</span>
    </template>

    <div class="map-area">
      <PaintMap :equipment="equipment" :positions="positions" :inactive="inactive" :hidden="hidden" @select="emit('select-equipment', $event)" />
    </div>

    <EquipmentStrip :equipment="equipment" @select="emit('select-equipment', $event)" />
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

/* 설계안 배치도 칸 비율 (1236 × 790) */
.map-area {
  position: relative;
  aspect-ratio: 1236 / 790;
}
</style>
