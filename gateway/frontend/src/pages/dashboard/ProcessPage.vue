<!--
  공정 3D 설비 배치도 화면 (예: 5공장 도장 공정 — 화면설계안 js/v-dash.js vProcess() 기준)
  - 왼쪽: 구역 현황(3D 설비 배치도 + 설비 줄, ProcessMapCard)
  - 오른쪽: 기준값 근접 센서(이 공정, 2026-11-18 센서별 단계 현황 대신), 오늘 알림 내역(이 공정), 오늘 설비 점검
  - 이 파일은 데이터 불러오기·합치기와 화면 이동만 담당
    (2026-11-19 재원님 요청: 배기팬을 눌러 회전수·켜기/끄기를 바꾸던 기능 삭제 — 배기팬은 가스 알림 때 서버 자동 제어)
  - 데이터 (지금은 예시 데이터): GET /processes/{id}/layout, /status, GET /alarms?processId=, GET /inspections/today
-->
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { USE_MOCK } from '@/api/mock'
import { getAlarms } from '@/api/dashboard'
import { getFactoryOverview } from '@/api/factory'
import { getProcessLayout, getProcessStatus, getTodayInspections } from '@/api/process'
import { getSiteName } from '@/api/sites'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import AlarmListCard from '@/features/dashboard/AlarmListCard.vue'
import NearThresholdCard from '@/features/process/NearThresholdCard.vue'
import InspectionTodayCard from '@/features/process/InspectionTodayCard.vue'
import ProcessMapCard from '@/features/process/ProcessMapCard.vue'
import { nearThresholdRows } from '@/features/process/nearThreshold'
import { buildProcessEquipment, layoutPlacement } from '@/features/process/processEquipment'
import { useLiveValues } from '@/features/equipment/useLiveValues'
import { useLiveReload } from '@/utils/useLiveReload'

const route = useRoute()
const router = useRouter()

const layout = ref(null)
const status = ref(null)
const alarms = ref([])
const inspections = ref(null)
const names = ref({ site: '', factory: '' })
const loading = ref(true)
const errorMessage = ref('')

/** 불러오기 (silent: 상황이 바뀌어 다시 부를 때는 뼈대를 띄우지 않음) */
async function load({ silent = false } = {}) {
  const { siteId, factoryId, processId } = route.params
  if (!silent) loading.value = true
  errorMessage.value = ''
  try {
    const [l, s, a, i, siteName, factory] = await Promise.all([
      getProcessLayout(processId),
      getProcessStatus(processId),
      getAlarms({ processId }),
      getTodayInspections(processId),
      getSiteName(siteId),
      getFactoryOverview(factoryId),
    ])
    layout.value = l
    status.value = s
    alarms.value = a.items
    inspections.value = i
    names.value = { site: siteName, factory: factory.factory.name }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

watch(() => route.params.processId, () => load(), { immediate: true })
useLiveReload(load)

// 화면용 설비 목록: 배치도(layout) + 지금 값(status) 을 합침 (features/process/processEquipment.js)
const equipment = computed(() => buildProcessEquipment(layout.value, status.value))
// 공장 관리에서 저장한 배치(위치 pos · 사용 안 함)를 배치도에 반영
const placement = computed(() => layoutPlacement(layout.value))

/**
 * 기준값 근접 센서 (features/process/nearThreshold.js)
 * - 지금 값 ÷ 주의 기준이 큰 순서로 5개, 값이 바뀌면 순위도 바로 바뀜
 * - 실시간 값
 *   · 예시 데이터 모드: 2초마다 값이 조금씩 흔들림 (useLiveValues, 설비 상세와 같은 방식)
 *   · 실제 서버: 5초마다 GET /processes/{id}/status 로 지금 값만 다시 받음
 *     TODO: 백엔드가 이미 보내는 SSE sensor.value(scope=process) 를 api/live.js 에 연결하면 이 타이머는 지움
 */
const allSensors = computed(() => equipment.value.flatMap((eq) => eq.sensors))
const { values: liveValues } = useLiveValues(() => allSensors.value)
const nearRows = computed(() => nearThresholdRows(equipment.value, liveValues.value))

const STATUS_POLL_MS = 5000
let statusTimer = null
if (!USE_MOCK) {
  statusTimer = setInterval(async () => {
    try {
      status.value = await getProcessStatus(route.params.processId)
    } catch {
      // 한 번 실패해도 다음 차례에 다시 받음 (화면은 마지막 값 그대로)
    }
  }, STATUS_POLL_MS)
}
onBeforeUnmount(() => clearInterval(statusTimer))

// 경로: 울산공장 › 5공장 › 도장 공정 3D 설비 배치도
const crumbs = computed(() => {
  const { siteId, factoryId } = route.params
  return [
    { label: names.value.site, to: { name: 'site-dashboard', params: { siteId } } },
    { label: names.value.factory, to: { name: 'dashboard', params: { siteId, factoryId } } },
    { label: layout.value?.name ?? '' }, // 공정 이름만 (2026-11-18 '… 3D 설비 배치도' 꼬리 뺌)
  ]
})

const inspectionRoute = computed(() => ({ name: 'inspection', params: { siteId: route.params.siteId } }))

/** 설비 선택 → 설비 상세 화면 */
function onSelectEquipment(eq) {
  router.push({ name: 'equipment', params: { ...route.params, equipmentId: eq.id } })
}

/** 기준값 근접 센서 줄 선택 → 그 센서가 붙은 설비 상세 화면 */
function onSelectNear(row) {
  onSelectEquipment({ id: row.equipmentId })
}
</script>

<template>
  <!-- 처음 들어올 때·주소가 바뀔 때만 뼈대 (조용히 다시 받을 때는 그대로) -->
  <PageSkeleton v-if="loading" shape="dashboard" layout="process" />
  <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>

  <div v-else class="process-page">
    <div class="left">
      <ProcessMapCard
        :crumbs="crumbs"
        :equipment="equipment"
        :positions="placement.positions"
        :inactive="placement.inactive"
        :hidden="placement.hidden"
        @select-equipment="onSelectEquipment"
      />
    </div>

    <div class="right">
      <NearThresholdCard :rows="nearRows" @select="onSelectNear" />
      <AlarmListCard class="fill" :alarms="alarms" />
      <InspectionTodayCard :process-name="layout.name" :inspections="inspections" :inspection-route="inspectionRoute" />
    </div>
  </div>

</template>

<style scoped>
/*
 * 배치: 사업장·공장 화면과 같음 (왼쪽 넓은 열 + 오른쪽 628px 열)
 * - 오른쪽 열 높이를 왼쪽 배치도 카드에 맞춤(stretch)
 * - 오늘 알림 내역이 남는 높이를 채워 오른쪽 아래 빈칸이 생기지 않게 함
 */
.process-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 628px;
  gap: 16px;
  align-items: stretch;
}

@media (max-width: 1400px) {
  .process-page {
    grid-template-columns: minmax(0, 1fr);
  }
}

.left,
.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* 오늘 알림 내역: 남는 높이를 채우고 목록만 스크롤 (AlarmListCard 의 .list, 구역 현황과 같은 방식) */
.fill {
  flex: 1 1 0;
  min-height: 240px;
  display: flex;
  flex-direction: column;
}

.fill :deep(.list) {
  flex: 1 1 0;
  max-height: none;
}

.state {
  color: var(--color-text-sub);
}

.state.error {
  color: var(--color-level-alarm-text);
}
</style>
