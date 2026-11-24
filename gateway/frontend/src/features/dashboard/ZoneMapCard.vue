<!--
  사업장 현황 지도 카드: 울산공장 지도 + 공장 말풍선(공정 목록)
  - 제목: 단계 경로 '사업장 › 울산공장' (CardCrumb, 2026-11-18 '구역 현황'에서 바꿈)
  - 2026-11-20 재원님 요청: 말풍선은 공장에 마우스를 올리면 뜨고, 공장을 누르면 바로 공장 화면으로
    · 지도에서 공장을 누르면 select-factory 이벤트 (페이지가 이동 또는 안내 처리)
    · 말풍선의 '공정 배치도 보기' 단추는 삭제 (누르면 바로 이동하므로)
  - 말풍선 두 가지
    · hovered: 마우스가 올라가 있는 공장
    · pinned : 마우스 없이 띄운 공장 — 처음 열 때 정상이 아닌 공장(설계안과 같음), 아래 '전체 공장 상태'에서 고른 공장
    · 보여 주는 것은 hovered 가 먼저. 지도 위에서 공장에 마우스를 올리면 pinned 는 지움
-->
<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import UlsanMap from './UlsanMap.vue'
import ZonePopover from './ZonePopover.vue'
import BaseCard from '@/components/BaseCard.vue'
import CardCrumb from '@/components/CardCrumb.vue'
import { STAGES } from '@/features/site/stages'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  siteName: { type: String, default: '' },
  /** 사업장 현황의 공장 목록 */
  factories: { type: Array, required: true },
  /** 공정 배치도로 들어갈 수 있는 공장 ID */
  enterableFactoryId: { type: Number, default: null },
})

const emit = defineEmits(['select-factory'])

const map = ref(null)

// 말풍선 { code, anchor } 또는 null — 위 주석의 hovered / pinned
const hovered = ref(null)
const pinned = ref(null)
const selected = computed(() => hovered.value ?? pinned.value)
const selectedFactory = computed(() => props.factories.find((f) => f.code === selected.value?.code) ?? null)

/** 지도에서 마우스 올림 (null 이면 공장 밖) */
function onHover(value) {
  hovered.value = value
  if (value) pinned.value = null
}

/** 지도에서 공장 클릭 → 공장 객체로 바꿔서 페이지에 알림 */
function onSelect(code) {
  const factory = props.factories.find((f) => f.code === code)
  if (factory) emit('select-factory', factory)
}

/** 공장 코드로 말풍선 열기 (아래 '전체 공장 상태' 카드에서 공장을 눌렀을 때 페이지가 부름) */
function open(code) {
  const anchor = map.value?.anchorOfCode(code)
  if (anchor) pinned.value = { code, anchor }
}
defineExpose({ open })

// 처음: 정상이 아닌 공장이 있으면 말풍선을 미리 띄움
onMounted(async () => {
  await nextTick()
  const abnormal = props.factories.find((f) => f.level !== LEVEL.NORMAL)
  if (abnormal) open(abnormal.code)
})
</script>

<template>
  <BaseCard>
    <template #title><CardCrumb :items="[{ label: siteName }]" /></template>
    <template #actions>
      <span v-for="s in STAGES" :key="s.level" class="legend">
        <i :style="{ borderColor: s.color }" />{{ s.label }}
      </span>
    </template>

    <div class="map-frame">
      <div class="map-area">
        <UlsanMap ref="map" :factories="factories" @hover="onHover" @select="onSelect" />
        <ZonePopover
          v-if="selected && selectedFactory"
          :factory="selectedFactory"
          :anchor="selected.anchor"
          :enterable="selectedFactory.id === enterableFactoryId"
        />
      </div>
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

/*
 * 카드가 화면 높이에 맞춰 늘어날 때 (페이지가 남는 높이를 이 카드에 나눠 줌)
 * - 지도는 비율 그대로 두고 남는 높이의 가운데에 둠 (.map-frame)
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

/* 지도 그림 비율(1545 : 715) 그대로 */
.map-area {
  position: relative;
  aspect-ratio: 1545 / 715;
}
</style>
