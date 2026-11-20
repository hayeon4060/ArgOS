<!--
  '설비 위치' 카드: 공정 배치도에서 이 설비만 크게 (설계안 vEquip() 설비 위치)
  - 그림은 공정 배치도와 같은 PaintMap (상태 테두리·센서 이름표 색 그대로), focus 로 이 설비 둘레만 보여 줌
  - 옆 설비를 누르면 select → 그 설비 상세로 이동 (페이지가 처리)
-->
<script setup>
import BaseCard from '@/components/BaseCard.vue'
import PaintMap from '@/features/process/PaintMap.vue'

defineProps({
  /** 공정의 화면용 설비 목록 (features/process/processEquipment.js) */
  equipment: { type: Array, required: true },
  /** 이 설비의 그림 안 이름 (예: 'top-oven') */
  focusKey: { type: String, required: true },
  /** 카드 설명 ('도장 공정 · 8번 설비') */
  sub: { type: String, default: '' },
  /** 공장 관리에서 저장한 위치 { 설비 이름: { x, y } } · 사용 안 함 (processEquipment.js layoutPlacement) */
  positions: { type: Object, default: () => ({}) },
  inactive: { type: Array, default: () => [] },
  /** 숨길 설비 그림 (등록되지 않은 설비) */
  hidden: { type: Array, default: () => [] },
})

const emit = defineEmits(['select'])
</script>

<template>
  <BaseCard title="설비 위치" :sub="sub">
    <div class="map">
      <!-- 배기팬을 누르면 그 설비로 이동 (대시보드의 배기팬 조절 창은 2026-11-19 삭제) -->
      <PaintMap :equipment="equipment" :focus="focusKey" :positions="positions" :inactive="inactive" :hidden="hidden" @select="emit('select', $event)" />
    </div>
  </BaseCard>
</template>

<style scoped>
.map {
  height: 380px;
}
</style>
