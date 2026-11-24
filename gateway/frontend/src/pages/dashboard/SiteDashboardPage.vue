<!--
  사업장 현황 화면 (예: 울산공장 — 화면설계안 js/v-dash.js vSite() 기준, 설계안 이름 '구역 현황')
  - 왼쪽: 구역 현황 지도(ZoneMapCard)
  - 오른쪽: 전일 시간대별 알림(HourlyAlarmCard), 오늘 알림 내역(AlarmListCard)
  - 아래: 전체 공장 상태(FactoryStatusCard)를 화면 끝까지 (2026-11-18 센서별 단계 현황 삭제)
  - 공장 이동 (2026-11-20): 지도에서 공장을 누르면 바로 공장 화면, 공정 배치도가 없는 공장은 안내 문구
  - 이 파일은 데이터 불러오기와 화면 이동만 담당, 모양은 features/dashboard 의 카드가 담당
  - 데이터 (지금은 예시 데이터): GET /sites/{siteId}/overview, GET /alarms, GET /alarms/hourly
  - TODO: 실시간(SSE) 연결 후 값이 바뀌면 카드가 다시 그려지게
-->
<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAlarms, getHourlyAlarms, getSiteOverview } from '@/api/dashboard'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import AppNotice from '@/components/AppNotice.vue'
import AlarmListCard from '@/features/dashboard/AlarmListCard.vue'
import FactoryStatusCard from '@/features/dashboard/FactoryStatusCard.vue'
import HourlyAlarmCard from '@/features/dashboard/HourlyAlarmCard.vue'
import ZoneMapCard from '@/features/dashboard/ZoneMapCard.vue'
import { useLiveReload } from '@/utils/useLiveReload'
import { useNotice } from '@/utils/useNotice'

const route = useRoute()
const router = useRouter()

/** 공정 배치도로 들어갈 수 있는 공장 (설계 범위: 5공장만) */
const ENTERABLE_FACTORY_ID = 5

const overview = ref(null)
const alarms = ref([])
const hourly = ref(null)
const loading = ref(true)
const errorMessage = ref('')

/**
 * 세 가지를 한꺼번에 불러옴 (하나라도 실패하면 오류 문구)
 * - silent: 상황이 바뀌어 다시 부를 때는 뼈대를 띄우지 않음 (카드가 다시 올라오지 않음)
 */
async function load({ silent = false } = {}) {
  const { siteId } = route.params
  if (!silent) loading.value = true
  errorMessage.value = ''
  try {
    const [o, a, h] = await Promise.all([
      getSiteOverview(siteId),
      getAlarms({ siteId }),
      getHourlyAlarms({ siteId }),
    ])
    overview.value = o
    alarms.value = a.items
    hourly.value = h
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

watch(() => route.params.siteId, () => load(), { immediate: true })
useLiveReload(load)

// 지도 카드 (공장 카드를 눌렀을 때 말풍선을 열려고 참조)
const zoneMap = ref(null)

/** 공장 대시보드로 이동 */
function enterFactory(factory) {
  router.push({ name: 'dashboard', params: { siteId: route.params.siteId, factoryId: factory.id } })
}

const { message: noticeMessage, show: showNotice } = useNotice()

/**
 * 지도에서 공장 클릭: 들어갈 수 있으면 바로 이동, 아니면 안내 문구
 * - 5공장 말고는 공장 화면 데이터(공정 배치도)가 없어 이동하면 오류 화면이 됨
 */
function onMapSelect(factory) {
  if (factory.id === ENTERABLE_FACTORY_ID) enterFactory(factory)
  else showNotice(`${factory.name}의 공정 배치도는 아직 등록되지 않았습니다.`)
}

/** 아래 공장 카드 클릭: 들어갈 수 있으면 이동, 아니면 지도에 말풍선 */
function onFactorySelect(factory) {
  if (factory.id === ENTERABLE_FACTORY_ID) enterFactory(factory)
  else zoneMap.value?.open(factory.code)
}
</script>

<template>
  <!-- 처음 들어올 때·주소가 바뀔 때만 뼈대 (조용히 다시 받을 때는 그대로) -->
  <PageSkeleton v-if="loading" shape="dashboard" layout="site-dashboard" />
  <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>

  <div v-else class="site-dashboard">
    <ZoneMapCard
      ref="zoneMap"
      :site-name="overview.site.name"
      :factories="overview.factories"
      :enterable-factory-id="ENTERABLE_FACTORY_ID"
      @select-factory="onMapSelect"
    />

    <div class="right">
      <HourlyAlarmCard :hourly="hourly" />
      <AlarmListCard class="fill" :alarms="alarms" />
    </div>

    <FactoryStatusCard class="wide" :factories="overview.factories" @select="onFactorySelect" />
  </div>

  <AppNotice :message="noticeMessage" />
</template>

<style scoped>
/*
 * 배치 (설계안 1920 화면 기준)
 * - 위 줄: 왼쪽 넓은 열(구역 현황 지도) + 오른쪽 628px 열
 * - 아래 줄: 전체 공장 상태가 두 열을 모두 차지
 * - 위 줄 높이는 지도 카드가 정하고, 오른쪽 오늘 알림 내역은 남는 높이를 채운 뒤 안에서 스크롤
 * - 좁은 화면에서는 위아래로
 */
.site-dashboard {
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
  .site-dashboard {
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
