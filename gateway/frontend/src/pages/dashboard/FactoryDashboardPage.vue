<!--
  공장 대시보드 화면 (예: 5공장 공정 배치도 — 화면설계안 js/v-dash.js vPlant() 기준)
  - 왼쪽: 구역 현황(공정 배치도, PlantMapCard)
  - 오른쪽: 전일 시간대별 알림, 오늘 알림 내역 (사업장 현황과 같은 순서·카드를 공장 범위로, 2026-11-18 순서 바꿈)
  - 아래: 공정별 설비 상태(ProcessStatusCard)를 화면 끝까지 (2026-11-18 센서별 단계 현황 삭제)
  - 이 파일은 데이터 불러오기와 화면 이동만 담당, 모양은 features 의 카드가 담당
  - 데이터 (지금은 예시 데이터): GET /factories/{factoryId}/overview, GET /alarms?factoryId=, GET /alarms/hourly?factoryId=
  - 공정을 누르면: 설비 배치도가 있는 공정(도장 공정)은 3D 설비 배치도로, 나머지는 안내 문구
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAlarms, getHourlyAlarms } from '@/api/dashboard'
import { getFactoryOverview } from '@/api/factory'
import { getSiteName } from '@/api/sites'
import AppNotice from '@/components/AppNotice.vue'
import AlarmListCard from '@/features/dashboard/AlarmListCard.vue'
import HourlyAlarmCard from '@/features/dashboard/HourlyAlarmCard.vue'
import PlantMapCard from '@/features/factory/PlantMapCard.vue'
import ProcessStatusCard from '@/features/factory/ProcessStatusCard.vue'
import { useLiveReload } from '@/utils/useLiveReload'
import { useNotice } from '@/utils/useNotice'

const route = useRoute()
const router = useRouter()

const overview = ref(null)
const alarms = ref([])
const hourly = ref(null)
const siteName = ref('')
const loading = ref(true)
const errorMessage = ref('')

/**
 * 네 가지를 한꺼번에 불러옴 (하나라도 실패하면 오류 문구)
 * - silent: 상황이 바뀌어 다시 부를 때는 '불러오는 중'을 띄우지 않음
 */
async function load({ silent = false } = {}) {
  const { siteId, factoryId } = route.params
  if (!silent) loading.value = true
  errorMessage.value = ''
  try {
    const [o, a, h, name] = await Promise.all([
      getFactoryOverview(factoryId),
      getAlarms({ factoryId }),
      getHourlyAlarms({ factoryId }),
      getSiteName(siteId),
    ])
    overview.value = o
    alarms.value = a.items
    hourly.value = h
    siteName.value = name
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

watch(() => route.params.factoryId, () => load(), { immediate: true })
useLiveReload(load)

// 경로의 '울산공장' → 사업장 현황
const siteRoute = computed(() => ({ name: 'site-dashboard', params: { siteId: route.params.siteId } }))

const { message: noticeMessage, show: showNotice } = useNotice()

/** 공정 선택: 설비 배치도가 있으면 3D 설비 배치도로, 없으면 안내 */
function onSelectProcess(process) {
  if (!process.hasFloorplan) {
    showNotice(`${process.name}의 설비 배치도는 아직 등록되지 않았습니다.`)
    return
  }
  router.push({
    name: 'process',
    params: { siteId: route.params.siteId, factoryId: route.params.factoryId, processId: process.id },
  })
}
</script>

<template>
  <p v-if="loading" class="state">불러오는 중</p>
  <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>

  <div v-else class="factory-dashboard">
    <PlantMapCard
      :site-name="siteName"
      :site-route="siteRoute"
      :factory-name="overview.factory.name"
      :processes="overview.processes"
      :cameras="overview.cameras"
      @select-process="onSelectProcess"
    />

    <div class="right">
      <HourlyAlarmCard :hourly="hourly" />
      <AlarmListCard class="fill" :alarms="alarms" />
    </div>

    <ProcessStatusCard
      class="wide"
      :factory-name="overview.factory.name"
      :processes="overview.processes"
      @select="onSelectProcess"
    />
  </div>

  <AppNotice :message="noticeMessage" />
</template>

<style scoped>
/*
 * 배치 (설계안 1920 화면 기준)
 * - 위 줄: 왼쪽 넓은 열(5공장 공정 배치도) + 오른쪽 628px 열
 * - 아래 줄: 공정별 설비 상태가 두 열을 모두 차지
 * - 위 줄 높이는 지도 카드가 정하고, 오른쪽 오늘 알림 내역은 남는 높이를 채운 뒤 안에서 스크롤
 * - 좁은 화면에서는 위아래로
 */
.factory-dashboard {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 628px;
  gap: 16px;
  align-items: stretch;
  /*
   * 화면 높이를 채움: 본문 높이(화면 − 상단 바 − 본문 위아래 여백 40px)만큼 최소 높이를 주고,
   * 남는 높이는 위 줄(지도 카드 · 오른쪽 알림 목록)이 가져감. 아래 줄 카드는 내용 높이 그대로
   * (전에는 내용 높이만큼만 그려서 큰 화면에서 아래가 비었음)
   */
  min-height: calc(var(--screen-h) - var(--topbar-height) - 40px);
  grid-template-rows: 1fr auto;
}

.wide {
  grid-column: 1 / -1;
}

@media (max-width: 1400px) {
  .factory-dashboard {
    grid-template-columns: minmax(0, 1fr);
    /* 한 열로 쌓일 때는 내용 높이대로 */
    min-height: 0;
    grid-template-rows: none;
  }
}

.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* 오늘 알림 내역: 남는 높이를 채우고 목록만 스크롤 (AlarmListCard 의 .list) */
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
