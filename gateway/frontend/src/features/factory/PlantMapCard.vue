<!--
  공장 지도 카드: 단계 경로(사업장 › 울산공장 › 5공장) + 범례 + 공정 배치도
  - 2026-11-18 제목 '구역 현황' + '5공장 공정 배치도' 경로 → 단계 경로 하나로 (CardCrumb)
  - 공정을 누르면 select-process 이벤트 (페이지가 3D 배치도로 이동 또는 안내)
  - 경로의 사업장 이름을 누르면 사업장 현황으로
-->
<script setup>
import PlantMap from './PlantMap.vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import CardCrumb from '@/components/CardCrumb.vue'
import { STAGES } from '@/features/site/stages'

defineProps({
  siteName: { type: String, default: '' },
  /** 사업장 현황으로 가는 주소 (RouterLink to) */
  siteRoute: { type: Object, required: true },
  factoryName: { type: String, required: true },
  processes: { type: Array, required: true },
  cameras: { type: Array, default: () => [] },
})

const emit = defineEmits(['select-process'])
</script>

<template>
  <BaseCard>
    <template #title>
      <CardCrumb :items="[{ label: siteName, to: siteRoute }, { label: factoryName }]" />
    </template>

    <template #actions>
      <span v-for="s in STAGES" :key="s.level" class="legend">
        <i :style="{ borderColor: s.color }" />{{ s.label }}
      </span>
      <span class="legend"><i class="camera" />열화상 카메라</span>
    </template>

    <div class="map-frame">
      <div class="map-area">
        <PlantMap :processes="processes" :cameras="cameras" @select="emit('select-process', $event)" />
      </div>
      <div class="hint"><AppIcon name="zoomin" :size="15" />공정을 누르면 설비 배치도로 들어갑니다</div>
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
  border: 2.5px solid;
  border-radius: 3px;
  background: #fff;
}

/* 열화상 카메라 범례: 분홍 네모 */
.legend i.camera {
  width: 16px;
  height: 11px;
  border: none;
  background: #db2777;
}

/*
 * 카드가 화면 높이에 맞춰 늘어날 때 (페이지가 남는 높이를 이 카드에 나눠 줌)
 * - 지도는 비율 그대로 두고 남는 높이의 가운데에 둠 (.map-frame)
 * - 안내 문구(.hint)는 지도 아래가 아니라 카드 왼쪽 아래 구석에 붙음
 */
.base-card {
  display: flex;
  flex-direction: column;
}

.map-frame {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.map-area {
  position: relative;
}

.hint {
  position: absolute;
  left: 16px;
  bottom: 14px;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.92);
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.hint .app-icon {
  color: var(--color-text-weak);
}
</style>
