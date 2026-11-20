<!--
  사업장 선택 화면 (본사 관리자 전용)
  - 화면설계안 js/v-rev.js vRegion() 의 평상시(화재 아님) 화면 기준
  - 배치: 왼쪽 위 지도(SiteMapCard) / 왼쪽 아래 센서 단계(SiteSensorCard) / 오른쪽 목록(SiteListCard)
  - 이 파일은 데이터 불러오기와 화면 이동만 담당, 모양은 features/site 의 각 카드가 담당
  - 이번 범위는 울산공장 5공장만 진입 가능, 나머지는 '준비 중' 안내
  - 2026-11-20 재원님 요청: 사업장 목록 '사업장 추가' → 이름 입력('창원' → '창원공장') → POST /sites → 그 사업장 공장 관리로 바로 이동
    · 공장이 하나도 없는 사업장은 '이동'을 누르면 공장 관리로 (볼 대시보드가 없어서)
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getActiveFires } from '@/api/emergency'
import { createSite, getSites } from '@/api/sites'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import AppNotice from '@/components/AppNotice.vue'
import NameInputModal from '@/components/NameInputModal.vue'
import SiteListCard from '@/features/site/SiteListCard.vue'
import SiteMapCard from '@/features/site/SiteMapCard.vue'
import SiteSensorCard from '@/features/site/SiteSensorCard.vue'
import FireBanner from '@/features/site/FireBanner.vue'
import FirePanel from '@/features/site/FirePanel.vue'
import { setFactoryCount } from '@/features/site/siteSetup'
import { useAlertStore } from '@/stores/alert'
import { toast } from '@/utils/toast'
import { useLiveReload } from '@/utils/useLiveReload'
import { useNotice } from '@/utils/useNotice'

const router = useRouter()

/** 지금 진입 가능한 사업장·공장 (설계 범위: 울산공장 5공장만) */
const AVAILABLE_SITE_ID = 1
const AVAILABLE_FACTORY_ID = 5

// 사업장 목록 (GET /sites)
const sites = ref([])
const loading = ref(true)
const errorMessage = ref('')

/**
 * 화재 (GET /fires?status=ACTIVE) — 있으면 위에 화재 띠, 오른쪽 목록 자리에 '화재 상세' (화면설계서 C5)
 * - 서버 시각 기준 경과 시간: 응답의 serverTime 과 내 시계 차이를 기억
 */
const fire = ref(null)
let serverOffset = 0

/** 사업장 목록 불러오기 (silent: 상황이 바뀌어 다시 부를 때는 뼈대를 띄우지 않음) */
async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    const [list, fires] = await Promise.all([getSites(), getActiveFires()])
    sites.value = list
    fire.value = fires[0] ?? null
    if (fire.value?.serverTime) serverOffset = new Date(fire.value.serverTime).getTime() - Date.now()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
useLiveReload(load) // 상황(정상·주의 등)이 바뀌면 숫자·색만 다시

/* ───────── 화재 경과 시간 (1초마다) ───────── */
const tick = ref(Date.now())
const timer = setInterval(() => (tick.value = Date.now()), 1000)
onBeforeUnmount(() => clearInterval(timer))
const pad = (n) => String(n).padStart(2, '0')
const serverNow = computed(() => new Date(tick.value + serverOffset))
const elapsed = computed(() => {
  if (!fire.value) return ''
  const sec = Math.max(0, Math.floor((serverNow.value.getTime() - new Date(fire.value.detectedAt).getTime()) / 1000))
  return `${pad(Math.floor(sec / 3600))}:${pad(Math.floor(sec / 60) % 60)}:${pad(sec % 60)}`
})
const nowText = computed(() => serverNow.value.toTimeString().slice(0, 8))

/** '5공장 대시보드로 이동': 그 공정 배치도로 + 화재 대응 창(열람) */
const alert = useAlertStore()
function goFire() {
  const f = fire.value
  router.push({ name: 'process', params: { siteId: f.siteId, factoryId: f.factoryId, processId: f.processId } })
  alert.open('alarm', { alarmId: f.alarmId })
}

// 짧은 안내 문구
const { message: noticeMessage, show: showNotice } = useNotice()

/**
 * 사업장으로 이동 (지도 핀, 도넛 카드, '이동' 버튼) → 사업장 현황 화면
 * - 지금은 울산공장만 들어갈 수 있음
 */
function enterSite(siteId) {
  const site = sites.value.find((s) => s.id === siteId)
  // 공장이 없는 사업장(막 추가한 곳): 공장 관리에서 공장부터 추가
  if (site?.factoryCount === 0) {
    router.push({ name: 'factory', params: { siteId } })
    return
  }
  if (siteId !== AVAILABLE_SITE_ID) {
    showNotice(`${site?.name ?? '이 사업장'} 화면은 준비 중입니다. 지금은 울산공장만 볼 수 있습니다.`)
    return
  }
  router.push({ name: 'site-dashboard', params: { siteId } })
}

/* ───────── 사업장 추가 (2026-11-20) ───────── */
const adding = ref(false)
const addBusy = ref(false)

/** 이름 입력 창 '추가' → 만든 사업장의 공장 관리로 (공장 0개 → 상단 메뉴는 '관리'만) */
async function addSite(name) {
  addBusy.value = true
  try {
    const made = await createSite({ name })
    setFactoryCount(made.id, 0)
    adding.value = false
    toast(`${name}을(를) 추가했습니다. 공장을 추가해 주세요.`)
    router.push({ name: 'factory', params: { siteId: made.id } })
  } catch (error) {
    toast(error.message, { type: 'err' }) // 409 CODE_TAKEN: 같은 이름
  } finally {
    addBusy.value = false
  }
}

/** 공장 줄을 눌렀을 때 → 공장 대시보드 (FactoryRow 가 진입 가능한 공장만 이벤트를 보냄) */
function enterFactory(factory) {
  router.push({ name: 'dashboard', params: { siteId: AVAILABLE_SITE_ID, factoryId: factory.id } })
}
</script>

<template>
  <div class="site-select">
    <!-- 처음 들어올 때·주소가 바뀔 때만 뼈대 (조용히 다시 받을 때는 그대로) -->
    <PageSkeleton v-if="loading" class="skeleton" shape="dashboard" layout="site-select" />
    <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>

    <template v-else>
      <FireBanner v-if="fire" :fire="fire" :elapsed="elapsed" class="banner" />
      <div class="left">
        <SiteMapCard :sites="sites" :current-id="AVAILABLE_SITE_ID" @select="enterSite" />
        <SiteSensorCard :sites="sites" @select="enterSite" />
      </div>
      <FirePanel v-if="fire" :fire="fire" :now="nowText" @go="goFire" />
      <SiteListCard
        v-else
        :sites="sites"
        :current-site-id="AVAILABLE_SITE_ID"
        :link-factory-id="AVAILABLE_FACTORY_ID"
        @enter="enterSite"
        @enter-factory="enterFactory"
        @add="adding = true"
      />
    </template>

    <AppNotice :message="noticeMessage" />

    <!-- 사업장 추가: 이름 끝에 '공장'을 붙임 (예: 창원 → 창원공장, 기존 울산공장 · 아산공장과 같은 꼴) -->
    <NameInputModal
      :open="adding"
      title="사업장 추가"
      label="사업장 이름"
      placeholder="예: 창원"
      suffix="공장"
      :taken="sites.map((s) => s.name)"
      :busy="addBusy"
      @submit="addSite"
      @cancel="adding = false"
    />
  </div>
</template>

<style scoped>
/* 배치: 왼쪽 700px 열 + 오른쪽 목록 (설계안 1920 화면 기준), 좁은 화면에서는 위아래로 */
.site-select {
  display: grid;
  grid-template-columns: minmax(0, 700px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1200px) {
  .site-select {
    grid-template-columns: minmax(0, 1fr);
  }
}

.banner {
  grid-column: 1 / -1;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 뼈대 · 안내 문구는 두 열을 모두 차지 (뼈대는 안에서 실제 화면과 같은 두 열로 나뉨) */
.skeleton {
  grid-column: 1 / -1;
}

.state {
  grid-column: 1 / -1;
  color: var(--color-text-sub);
}

.state.error {
  color: var(--color-level-alarm-text);
}
</style>
