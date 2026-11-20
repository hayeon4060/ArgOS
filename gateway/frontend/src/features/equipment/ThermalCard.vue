<!--
  '열화상 카메라' 카드 (설계안 vEquip() 열화상)
  - 제목줄 오른쪽: 이 설비 구역의 최고 · 최저 온도 (구역이 정해지지 않은 설비는 없음)
  - 그림: ThermalImage (LIVE, 구역 이름, 색 눈금, 최고 지점 표시) — 3단계(경보 · 화재)면 VMS 영상 (thermalStream.js)
  - 아래: 구역 설명 + 마지막 수신 시각
-->
<script setup>
import { computed } from 'vue'
import ThermalImage from './ThermalImage.vue'
import BaseCard from '@/components/BaseCard.vue'
import { liveStreamUrl } from './thermalStream'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  /** 공정 이름 ('도장 공정') — 설비 응답 path 에서 (화면이 넘겨 줌) */
  processName: { type: String, default: '' },
  /** GET /thermal/{cameraId}/latest 응답 */
  thermal: { type: Object, required: true },
  /** 설비 단계 (최고 온도 글자 색) */
  level: { type: String, required: true },
  /** 화면을 연 뒤 지난 초 (실시간 갱신 흉내: 촬영 시각에 더해 보여 줌) */
  elapsedSec: { type: Number, default: 0 },
})

// 최고 온도 글자 색: 주의 주황, 경보·화재 빨강
const hot = computed(() => props.level !== LEVEL.NORMAL)
const danger = computed(() => props.level === LEVEL.ALARM || props.level === LEVEL.FIRE)

/** 마지막 수신 시각 'HH:mm:ss' (촬영 시각 + 지난 초) */
const time = computed(() => {
  const d = new Date(props.thermal.capturedAt)
  d.setSeconds(d.getSeconds() + props.elapsedSec)
  return d.toTimeString().slice(0, 8)
})
</script>

<template>
  <BaseCard title="열화상 카메라" :sub="[processName, thermal.tag].filter(Boolean).join(' ')">
    <template v-if="thermal.max != null" #actions>
      <span>최고&nbsp;<b class="mono temp" :class="{ hot, danger }">{{ thermal.max }} ℃</b></span>
      <span>최저&nbsp;<b class="mono temp">{{ thermal.min }} ℃</b></span>
    </template>

    <div class="body">
      <ThermalImage
        :image-url="thermal.imageUrl"
        :stream-url="liveStreamUrl(thermal, level)"
        :max="thermal.max"
        :max-point="thermal.maxPoint"
        :scale="thermal.scale"
        :zone-name="thermal.zone?.name ?? `${processName || '공정'} 전체`"
      />
      <div class="foot">
        <span>{{ thermal.zone?.desc ?? '공정마다 열화상 카메라 1대로 전체를 봅니다' }}</span>
        <span class="mono">{{ time }}</span>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.temp {
  font-size: 15px;
  color: var(--color-text);
}

.temp.hot {
  color: var(--color-level-warn-text);
}

.temp.danger {
  color: var(--color-level-alarm-text);
}

.body {
  padding: 16px 20px 0;
}

.foot {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 0 16px;
  font-size: 12.5px;
  color: #64748b;
}
</style>
