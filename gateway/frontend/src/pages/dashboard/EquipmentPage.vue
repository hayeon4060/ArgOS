<!--
  설비 상세 화면 (예: 상도 건조로 — 화면설계안 js/v-dash.js vEquip(), 화면설계서 B4)
  - 제목 줄: 경로 + 상태 + 체크리스트 진행 + 설비 기준값 관리 + 설비 배치도로 (EquipmentTopBar)
  - '설비 기준값 관리'(제목 줄 버튼 · 이벤트 이력의 '기준값 관리에서 보기') → 공장 관리와 같은 기준값 관리 창을
    이 화면 위에 엶 (다른 화면으로 이동 안 함, 2026-11-19 재원님 요청)
  - 위 줄: 설비 위치 | 센서 현재값(게이지 3개) | 열화상 카메라
  - 아래 줄: 센서 추이(범위 1시간 / 6시간 / 오늘, 전날 겹쳐 보기) | 이벤트 이력
  - 이 파일은 데이터 불러오기와 화면 이동만 담당, 그리는 일은 features/equipment/ 의 카드들이 함
  - 데이터 (지금은 예시 데이터): GET /equipment/{id}, /equipment/{id}/events, /thermal/{cameraId}/latest,
    /sensors/series, 설비 위치 그림용 GET /processes/{id}/layout, /status
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEquipment, getEquipmentEvents, getSensorSeries, getThermalLatest } from '@/api/equipment'
import { getProcessLayout, getProcessStatus } from '@/api/process'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import EquipmentLocationCard from '@/features/equipment/EquipmentLocationCard.vue'
import EquipmentTopBar from '@/features/equipment/EquipmentTopBar.vue'
import EventLogCard from '@/features/equipment/EventLogCard.vue'
import ThresholdModal from '@/features/threshold/ThresholdModal.vue'
import SensorGaugeCard from '@/features/equipment/SensorGaugeCard.vue'
import ThermalCard from '@/features/equipment/ThermalCard.vue'
import TrendCard from '@/features/equipment/TrendCard.vue'
import { withShownLevels, worseLevel } from '@/features/equipment/sensorLevel'
import { useLiveValues } from '@/features/equipment/useLiveValues'
import { buildProcessEquipment, layoutPlacement } from '@/features/process/processEquipment'
import { useLiveReload } from '@/utils/useLiveReload'

const route = useRoute()
const router = useRouter()

const equipment = ref(null)
const events = ref([])
const thermal = ref(null)
const processEquipment = ref([])
const placement = ref({ positions: {}, inactive: [], hidden: [] })
// 배치도 안에서 이 설비를 가리키는 이름 (그림에 원래 있는 설비는 그림 이름, 공장 관리에서 추가한 설비는 'x' + id)
const focusKey = computed(() => processEquipment.value.find((e) => e.id === equipment.value?.id)?.key ?? '')
const loading = ref(true)
const errorMessage = ref('')

/** 설비 정보·이벤트·열화상·설비 위치 그림 불러오기 (silent: 상황이 바뀌어 다시 부를 때는 뼈대 없이) */
async function load({ silent = false } = {}) {
  const { processId, equipmentId } = route.params
  if (!silent) loading.value = true
  errorMessage.value = ''
  try {
    const [eq, ev, layout, status] = await Promise.all([
      getEquipment(equipmentId),
      getEquipmentEvents(equipmentId),
      getProcessLayout(processId),
      getProcessStatus(processId),
    ])
    // 주소의 공정과 설비의 공정이 다르면 (주소를 직접 고친 경우) 열지 않음
    // - 응답에 processId 가 있을 때만 비교 (지금 백엔드 GET /equipment/{id} 응답에는 없음 → backend-notes 8장)
    if (eq.processId != null && eq.processId !== Number(processId)) throw new Error('이 공정의 설비가 아닙니다.')
    equipment.value = eq
    events.value = ev
    processEquipment.value = buildProcessEquipment(layout, status)
    placement.value = layoutPlacement(layout) // 공장 관리에서 옮긴 자리·사용 안 함
    thermal.value = eq.cameraId ? await getThermalLatest(eq.cameraId, eq.id) : null
    await loadSeries()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

watch(() => route.params.equipmentId, () => load(), { immediate: true })
useLiveReload(load)

/* ───────── 센서 추이: 범위(1시간 / 6시간 / 오늘)를 바꾸면 다시 불러옴 ───────── */
const range = ref('1h') // 설계안 기본값 (화면설계서는 '오늘'이 기본 → 보고서 '확인할 점')
const series = ref(null)
const seriesRange = ref('1h') // 지금 그려진 값의 범위 (새 값이 올 때까지 이전 그림 유지)
const seriesLoading = ref(false)

async function loadSeries() {
  if (!equipment.value?.sensors.length) {
    series.value = { series: [] }
    return
  }
  const want = range.value
  seriesLoading.value = true
  try {
    const data = await getSensorSeries({ equipment: equipment.value, range: want })
    if (want !== range.value) return // 그사이 범위를 또 바꿈
    series.value = data
    seriesRange.value = want
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    seriesLoading.value = false
  }
}

watch(range, loadSeries)

/* ───────── 실시간 값 (예시: 2초마다 조금씩) ───────── */
const { values: liveValues, elapsedSec } = useLiveValues(() => equipment.value?.sensors)

/*
 * 화면에 보여 줄 단계 (2026-11-20): 서버 단계 + 지금 값이 기준을 넘었는지 → 더 높은 쪽 (sensorLevel.js)
 * - 게이지 · 추이 · 제목 줄 상태 배지 · 설비 위치 그림의 이 설비가 모두 같은 단계를 씀
 * - 열화상 카드는 카메라 장면 색이라 서버 단계 그대로 (온도 센서 값만 넘었는데 '최고 52 ℃'가 주황이 되지 않게)
 */
const shown = computed(() => (equipment.value ? withShownLevels(equipment.value, liveValues.value) : null))

/** 설비 위치 그림: 이 설비 칸도 위와 같은 단계로 (다른 설비는 공정 상태 그대로) */
const locationEquipment = computed(() =>
  processEquipment.value.map((e) => (e.id === shown.value?.id ? { ...e, level: worseLevel(e.level, shown.value.level) } : e)),
)

/* ───────── 경로·링크 ───────── */
const crumbs = computed(() => {
  const { siteId, factoryId, processId } = route.params
  const [site, factory, process] = equipment.value?.path ?? []
  return [
    { label: site, to: { name: 'site-dashboard', params: { siteId } } },
    { label: factory, to: { name: 'dashboard', params: { siteId, factoryId } } },
    { label: process, to: { name: 'process', params: { siteId, factoryId, processId } } },
    { label: equipment.value?.name ?? '' },
  ]
})

const backTo = computed(() => {
  const { siteId, factoryId, processId } = route.params
  return { name: 'process', params: { siteId, factoryId, processId } }
})

/* ───────── 설비 기준값 관리 창 (이 화면 위에 뜸) ───────── */
const thresholdOpen = ref(false)
// 창 위쪽 위치 글자 '5공장 · 도장 공정' (공장 관리와 같은 모양)
const thresholdPath = computed(() => (equipment.value?.path ?? []).slice(1, 3).join(' · '))

// 통계 화면에 이 설비를 골라 둔 채로 이동
const historyTo = computed(() => ({ name: 'history', params: { siteId: route.params.siteId }, query: { equipmentId: equipment.value?.id } }))

const locationSub = computed(() => {
  const seq = equipment.value?.seq
  return `${equipment.value?.path?.[2] ?? ''}${seq ? ` · ${seq}번 설비` : ''}`
})

/** 설비 위치 그림에서 옆 설비를 누르면 그 설비 상세로 */
function onSelectEquipment(eq) {
  if (eq.id === equipment.value?.id) return
  router.push({ name: 'equipment', params: { ...route.params, equipmentId: eq.id } })
}
</script>

<template>
  <!-- 처음 들어올 때·주소가 바뀔 때만 뼈대 (조용히 다시 받을 때는 그대로) -->
  <PageSkeleton v-if="loading" shape="dashboard" layout="equipment" />
  <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>

  <div v-else class="equipment-page">
    <EquipmentTopBar
      :crumbs="crumbs"
      :level="shown.level"
      :checklist="equipment.activeChecklist"
      :back-to="backTo"
      @thresholds="thresholdOpen = true"
    />

    <div class="grid">
      <EquipmentLocationCard
        class="location"
        :equipment="locationEquipment"
        :focus-key="focusKey"
        :positions="placement.positions"
        :inactive="placement.inactive"
        :hidden="placement.hidden"
        :sub="locationSub"
        @select="onSelectEquipment"
      />
      <SensorGaugeCard class="gauges" :sensors="shown.sensors" :live-values="liveValues" />
      <ThermalCard v-if="thermal" class="thermal" :thermal="thermal" :process-name="equipment.path?.[2] ?? ''" :level="equipment.level" :elapsed-sec="elapsedSec" />

      <TrendCard
        v-model:pick="range"
        class="trend"
        :sensors="shown.sensors"
        :data="series"
        :range="seriesRange"
        :loading="seriesLoading"
        :live-values="liveValues"
      />
      <EventLogCard class="events" :events="events" :history-to="historyTo" @threshold="thresholdOpen = true" />
    </div>

    <!-- 설비 기준값 관리 창: 저장하면 서버가 SSE(threshold.changed · equipment.updated)를 보내 이 화면도 조용히 다시 불러옴 -->
    <ThresholdModal
      v-if="thresholdOpen"
      :equipment-id="equipment.id"
      :factory-id="Number(route.params.factoryId)"
      :path="thresholdPath"
      @close="thresholdOpen = false"
    />
  </div>
</template>

<style scoped>
/*
 * 배치 (설계안 1920 화면 기준): 위 줄 560 | 820 | 468, 아래 줄 추이가 앞 두 칸을 차지
 * - 고정 px 대신 같은 비율(fr)로: 1920 에서는 설계안과 같고, 1536 처럼 좁으면 세 칸이 같이 줄어듦
 *   (가운데만 줄이면 게이지 숫자가 고리와 겹침)
 * - 좁은 화면(1400px 이하)에서는 다른 화면처럼 한 줄에 하나씩
 */
.grid {
  display: grid;
  grid-template-columns: minmax(0, 560fr) minmax(0, 820fr) minmax(0, 468fr);
  gap: 16px; /* 같은 줄 카드는 높이를 맞춤 (설계안: 위 줄 428, 아래 줄 488) */
}

.trend {
  grid-column: 1 / 3;
}

@media (max-width: 1400px) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .trend {
    grid-column: auto;
  }
}

.state {
  color: var(--color-text-sub);
}

.state.error {
  color: var(--color-level-alarm-text);
}
</style>
