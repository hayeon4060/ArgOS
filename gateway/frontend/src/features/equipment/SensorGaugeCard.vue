<!--
  '센서 현재값' 카드: 전류 · 온도 · CO 게이지 3칸 (설계안 vEquip() #cg)
  - 이 설비에 없는 종류는 '센서 없음' 칸으로 둠 (자리 그대로)
  - 같은 종류 센서가 여러 개인 설비는 아직 없음 → 첫 번째 센서만 보여 줌 ('외 1개' 단추는 아직 안 만듦)
-->
<script setup>
import SensorGauge from './SensorGauge.vue'
import BaseCard from '@/components/BaseCard.vue'
import { STAGES } from '@/features/site/stages'
import { SENSOR_TYPES } from '@/types/codes'

const props = defineProps({
  /** 설비 상세의 센서 목록 */
  sensors: { type: Array, required: true },
  /** 실시간 값 { 센서 id: 값 } */
  liveValues: { type: Object, default: () => ({}) },
})

const sensorOf = (type) => props.sensors.find((s) => s.type === type) ?? null
</script>

<template>
  <BaseCard title="센서 현재값" sub="전류 · 온도 · CO">
    <template #actions>
      <span v-for="s in STAGES" :key="s.level" class="legend"><i :style="{ borderColor: s.color }" />{{ s.label }}</span>
    </template>

    <div class="gauge-row">
      <SensorGauge
        v-for="t in SENSOR_TYPES"
        :key="t.type"
        :type="t"
        :sensor="sensorOf(t.type)"
        :value="sensorOf(t.type) ? liveValues[sensorOf(t.type).id] : null"
      />
    </div>
  </BaseCard>
</template>

<style scoped>
/* 설계안: 256px 칸 3개, 6px 간격, 위 14px */
.gauge-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 14px 20px 20px;
}

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
