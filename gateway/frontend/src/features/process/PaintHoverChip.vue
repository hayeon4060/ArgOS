<!--
  도장 공정 3D 배치도: 마우스를 올린 설비 위에 뜨는 이름 칩 (2026-11-19 재원님 시안 A, 이전 말풍선 대신)
  - 설비 위: 이름 + 상태 배지 / 묶인 부품('배기팬 · 버너실 포함') / 센서 값('전류 22.4 A · 온도 146 ℃')
  - (2026-11-19 배기팬 위 칩 '누르면 회전수 · 켜기/끄기 조절'은 대시보드 배기팬 조절 창과 함께 삭제)
  - 자리(at)는 PaintMap 이 정함: 설비 위쪽 가운데. 마우스를 따라다니지 않음
-->
<script setup>
import { computed } from 'vue'
import { shapeOf } from './paintLayout'
import LevelBadge from '@/components/LevelBadge.vue'
import { SENSOR_TYPES } from '@/types/codes'

const props = defineProps({
  /** 화면용 설비 하나 { key, name, level, sensors } */
  equipment: { type: Object, required: true },
  /** 칩 아래 가운데 점 (그림 상자 안 좌표) */
  at: { type: Object, required: true },
})

// 그림 부품 id 앞부분 → 부품 이름 (건물 eq- 은 설비 이름이라 뺌)
const PART_NAME = { 'fan-': '배기팬', 'burner-': '버너실', 'ir-': '열화상 카메라', 'pump-': '순환펌프' }

/** 묶인 부품: '배기팬 · 버너실 포함' (부품이 없으면 '') */
const parts = computed(() => {
  const ids = shapeOf(props.equipment.shape ?? props.equipment.key)?.ids ?? [] // 추가한 설비는 그림 이름(shape)으로
  const names = ids.map((id) => Object.entries(PART_NAME).find(([head]) => id.startsWith(head))?.[1]).filter(Boolean)
  return names.length ? `${names.join(' · ')} 포함` : ''
})

/** 센서 값 한 줄: '전류 22.4 A · 온도 146 ℃' */
const values = computed(() => {
  const list = props.equipment.sensors
  if (!list.length) return '연결된 센서 없음'
  return list.map((s) => `${SENSOR_TYPES.find((t) => t.type === s.type)?.label ?? s.type} ${s.value} ${s.unit}`).join(' · ')
})
</script>

<template>
  <div class="hover-chip" :style="{ left: `${at.x}px`, top: `${at.y}px` }">
    <div class="head">
      <b>{{ equipment.name }}</b>
      <LevelBadge :level="equipment.level" />
    </div>
    <div v-if="parts" class="parts">{{ parts }}</div>
    <div class="values mono">{{ values }}</div>
  </div>
</template>

<style scoped>
/* 흰 칩 + 초록 테두리 (시안 A 의 선택 색) — 아래 가운데가 at 점에 오도록 */
.hover-chip {
  position: absolute;
  z-index: 8;
  transform: translate(-50%, calc(-100% - 14px));
  padding: 7px 12px 8px;
  border: 1.5px solid var(--color-level-normal);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12.5px;
  white-space: nowrap;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.16);
  pointer-events: none;
  animation: chip-in var(--m-pop-in) both;
}

@keyframes chip-in {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-100% - 10px));
  }
}

.head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.head b {
  font-size: 14px;
}

.parts {
  margin-top: 2px;
  color: var(--color-text-sub);
}

.values {
  margin-top: 4px;
  color: var(--color-text);
}

@media (prefers-reduced-motion: reduce) {
  .hover-chip {
    animation: none;
  }
}
</style>
