<!--
  통계·이력 관리 화면 (화면설계안 v-pages.js vStats(), 화면설계서 '통계·이력 관리', API 명세서 D1)
  - 이 화면은 조건을 들고 데이터를 불러오고 화면을 옮기는 일만 함. 그리는 부분은 features/history/
  - 배치 (2026-11-19 개편): 조건 줄 / 요약 4칸 / 왼쪽 알림 이력(두 줄 높이) | 오른쪽 위 일별 추이 · 아래 공정별 알림
    요일·시간대 카드는 뺌. 알림 이력의 '상세'를 누르면 오른쪽 두 카드 위에 상세 패널이 덮임
  - 오른쪽 위 '내보내기': 지금 기간·조건의 알림 이력 상세를 CSV 로 바로 저장 (바탕화면 저장 창, utils/saveFile.js)
  - 기간 단추·선택 상자를 바꾸면 바로 조회 (설계안), '조회' 단추는 같은 조건으로 다시 불러오기
  - 공장 담당자는 공장이 담당 공장으로 고정, 본사 관리자는 '전체 공장' 도 고를 수 있음 (처음은 5공장 — 설계안)
  - 주소에 ?factoryId=&processId= 가 있으면 그 공장·공정을 골라 둔 채로 시작 (factoryId=all → 전체 공장)
    (대시보드 '오늘 알림 내역' 카드의 '과거 알림' 단추, 2026-11-19)
  - 알림 상황이 바뀌면 조용히 다시 불러옴 (useLiveReload)
-->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getScopeTree } from '@/api/sites'
import { exportStats, getStatsAlarms, getStatsByProcess, getStatsSummary, getStatsTrend } from '@/api/stats'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import { saveFile } from '@/utils/saveFile'
import { toast } from '@/utils/toast'
import { useLiveReload } from '@/utils/useLiveReload'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import AlarmDetailPanel from '@/features/history/AlarmDetailPanel.vue'
import AlarmHistoryTable from '@/features/history/AlarmHistoryTable.vue'
import FilterBar from '@/features/history/FilterBar.vue'
import KpiCards from '@/features/history/KpiCards.vue'
import MonthlyReportModal from '@/features/history/MonthlyReportModal.vue'
import ProcessCard from '@/features/history/ProcessCard.vue'
import TrendCard from '@/features/history/TrendCard.vue'
import { PERIOD_LABEL, criteriaOf, rangeOf, ymd } from '@/features/history/historyFilter'

const PER_PAGE = 14 // 표 한 쪽 행 수 (왼쪽 열 전체 높이)

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const siteId = computed(() => route.params.siteId)

/* ───────── 조건 ───────── */
const lockFactory = computed(() => auth.role !== ROLE.HQ_ADMIN)
const factories = ref([])
const filter = reactive({ period: '30d', ...rangeOf('30d'), factoryId: '', processId: '', sensorType: '', level: '', shift: '' })
const criteria = computed(() => criteriaOf(filter, siteId.value))

/** 조건 줄에서 바꾼 값 반영 → 바로 조회 */
function change(patch) {
  Object.assign(filter, patch)
  if (patch.period && patch.period !== 'custom') Object.assign(filter, rangeOf(patch.period))
  query()
}

/* ───────── 카드 데이터 (요약·추이·공정별) ───────── */
const data = reactive({ summary: null, trend: null, byProcess: null })
const busy = ref(false)
let seq = 0 // 늦게 온 이전 조회 결과는 버림

/** 처음 조회(카드 + 알림 이력 표)가 끝났는지 — 끝나기 전에는 화면 대신 뼈대 (실패해도 끝난 것으로) */
const ready = ref(false)

async function query({ silent = false } = {}) {
  const my = ++seq
  if (!silent) busy.value = true
  const c = criteria.value
  try {
    const [summary, trend, byProcess] = await Promise.all([getStatsSummary(c), getStatsTrend(c), getStatsByProcess(c)])
    if (my !== seq) return
    Object.assign(data, { summary, trend, byProcess })
  } catch (error) {
    // 조회 실패: 이전 결과는 그대로 두고 알림만 (화면설계서 '조회 실패')
    if (my === seq) toast(`조회하지 못했습니다. ${error.message}`, { type: 'err' })
  } finally {
    if (my === seq) busy.value = false
  }
  if (my !== seq) return
  // 새 조건이면 표는 1쪽부터 (쪽이 바뀌면 아래 watch 가 표를 불러옴)
  if (!silent && table.page !== 1) table.page = 1
  else await loadTable()
  ready.value = true
}

/* ───────── 알림 이력 표 (유형·탭·쪽은 표만 다시 불러옴) ───────── */
const table = reactive({ rows: null, total: 0, hazard: '', tab: '', page: 1, busy: false, serverTime: '' })
let tableSeq = 0

async function loadTable() {
  const my = ++tableSeq
  table.busy = true
  try {
    const r = await getStatsAlarms({ ...criteria.value, hazard: table.hazard, status: table.tab, page: table.page - 1, size: PER_PAGE })
    if (my !== tableSeq) return
    table.rows = r.items
    table.total = r.total
    table.serverTime = r.serverTime ?? '' // 있으면 '경과' 분을 서버 시계로 (없으면 PC 시계)
  } catch (error) {
    if (my === tableSeq) toast(error.message, { type: 'err' })
  } finally {
    if (my === tableSeq) table.busy = false
  }
}
watch(() => [table.hazard, table.tab, table.page], loadTable)

/* ───────── 알림 상세 패널 ───────── */
const selectedId = ref(null)
/** 상세 단추(또는 줄) → 패널 열기, 열린 줄을 다시 누르면 닫기 */
function openRow(row) {
  selectedId.value = selectedId.value === row.id ? null : row.id
}

/** 패널의 '설비 상세 화면' → 설비 상세 (지금 화면이 있는 5공장 도장 공정 설비만) */
function openEquipment(d) {
  if (d.factoryId === 5 && d.processId === 12 && d.equipmentId) {
    router.push({ name: 'equipment', params: { siteId: siteId.value, factoryId: 5, processId: 12, equipmentId: d.equipmentId } })
    return
  }
  toast('이 설비의 상세 화면은 준비 중입니다. 지금은 5공장 도장 공정만 볼 수 있습니다.', { type: 'info' })
}

/* ───────── 카드 설명 · 화면용 값 ───────── */
const periodLabel = computed(() => PERIOD_LABEL[filter.period])
const isToday = computed(() => filter.from === filter.to && filter.to === ymd(new Date()))
/** 오늘(시간별)이면 아직 오지 않은 시간 칸은 뺌 (지금 시각과 마지막 알림 시각 중 늦은 쪽까지) */
const trendView = computed(() => {
  const t = data.trend
  if (!t || t.unit !== 'HOUR' || !isToday.value) return t
  const hourOf = (p) => Number(p.key.slice(11, 13))
  const lastWithData = Math.max(-1, ...t.points.filter((p) => p.warn + p.alarm > 0).map(hourOf))
  const end = Math.max(new Date().getHours(), lastWithData)
  return { ...t, points: t.points.filter((p) => hourOf(p) <= end) }
})
const trendSub = computed(() => periodLabel.value + ({ HOUR: ' · 시간별', WEEK: ' · 주별' }[data.trend?.unit] ?? ''))

/* ───────── 내보내기 (알림 이력 상세 CSV) · 월별 보고서 창 ───────── */
const exporting = ref(false)
async function exportHistory() {
  if (exporting.value) return
  exporting.value = true
  const c = criteria.value
  const name = `알림이력_${c.from.replace(/-/g, '')}-${c.to.replace(/-/g, '')}.csv`
  try {
    // 저장 창부터 열고(바탕화면), 파일 내용은 그다음 서버에서 받음
    const result = await saveFile(name, async () => (await exportStats(c)).blob)
    if (result === 'saved') toast(`${name} 파일을 저장했습니다.`)
    if (result === 'downloaded') toast(`${name} 파일을 다운로드 폴더에 저장했습니다.`)
  } catch (error) {
    toast(`내보내지 못했습니다. ${error.message}`, { type: 'err' })
  } finally {
    exporting.value = false
  }
}
const reportOpen = ref(false)

/**
 * 주소로 받은 공장·공정 미리 고르기 (대시보드 '과거 알림' 단추)
 * - 고를 수 있는 공장·공정일 때만 반영 (공장 담당자의 다른 공장 · 없는 공정은 무시하고 처음 값 그대로)
 */
function applyQuery() {
  const { factoryId, processId } = route.query
  if (factoryId === 'all') {
    if (!lockFactory.value) filter.factoryId = ''
    return
  }
  const factory = factories.value.find((f) => String(f.id) === String(factoryId))
  if (!factory) return
  filter.factoryId = String(factory.id)
  if (factory.processes?.some((p) => String(p.id) === String(processId))) filter.processId = String(processId)
}

/* ───────── 처음 불러오기 ───────── */
onMounted(async () => {
  try {
    // 공장 담당자는 담당 공장만 옴 (getScopeTree — 실서버 /tree 는 본사 관리자 전용)
    const tree = await getScopeTree(siteId.value)
    factories.value = tree.factories
    // 공장 담당자: 담당 공장 / 본사 관리자: 5공장(설계안 처음 값), 없으면 전체 공장
    const first = lockFactory.value ? factories.value[0] : factories.value.find((f) => f.id === 5)
    filter.factoryId = first ? String(first.id) : ''
    applyQuery()
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
  query()
})

// 예시 상황이 바뀌면(주의 발생·조치 등) 쪽은 그대로 두고 조용히 다시 불러옴
useLiveReload(() => query({ silent: true }))
</script>

<template>
  <!-- 처음 들어올 때만 뼈대 (다시 조회할 때는 아래 카드를 흐리게, 조용히 다시 받을 때는 그대로) -->
  <PageSkeleton v-if="!ready" shape="table" layout="history" />

  <div v-else class="history-page">
    <FilterBar
      :filter="filter"
      :factories="factories"
      :lock-factory="lockFactory"
      :busy="busy"
      @change="change"
      @query="query()"
      @export="exportHistory"
      @report="reportOpen = true"
    />

    <div class="cards" :class="{ loading: busy }">
      <KpiCards :summary="data.summary" :points="data.trend?.points ?? []" :today="filter.period === 'today'" />
      <div class="main">
        <!-- 왼쪽: 알림 이력 (두 줄 높이) -->
        <AlarmHistoryTable
          v-model:hazard="table.hazard"
          v-model:tab="table.tab"
          v-model:page="table.page"
          :rows="table.rows"
          :total="table.total"
          :per-page="PER_PAGE"
          :busy="table.busy"
          :server-time="table.serverTime"
          :selected-id="selectedId"
          @open="openRow"
        />
        <!-- 오른쪽: 일별 추이 · 공정별 알림, 상세 패널이 그 위를 덮음 -->
        <div class="side">
          <TrendCard :trend="trendView" :sub="trendSub" :show-shift="!filter.shift" />
          <ProcessCard :rows="data.byProcess" :sub="`센서 종류별 · ${periodLabel} 합계`" />
          <Transition name="panel">
            <AlarmDetailPanel
              v-if="selectedId"
              class="detail"
              :alarm-id="selectedId"
              @close="selectedId = null"
              @equipment="openEquipment"
            />
          </Transition>
        </div>
      </div>
    </div>
    <MonthlyReportModal :open="reportOpen" :factory-id="filter.factoryId" @close="reportOpen = false" />
  </div>
</template>

<style scoped>
/*
 * 배치 (설계안 1920 기준): 왼쪽 1180 | 오른쪽 684 — 같은 비율로 화면 폭에 맞춰 줄어듦
 * 1400px 이하는 한 줄에 하나씩
 */
/*
 * 한 화면에 맞춤 (2026-11-20 재원님 요청: 전체 화면이 스크롤되지 않게)
 * - 페이지 높이 = 화면 − 상단 바 − 본문 위아래 여백 40px. 너무 낮은 창에서는 640px 아래로 줄이지 않고 스크롤
 * - 조건 줄 · 요약 카드는 제 높이, 남는 높이는 아래 줄(.main)이 가져감
 *   → 알림 이력 표는 줄 높이를 줄여 14행을 맞추고(AlarmHistoryTable), 오른쪽 그래프 2개는 높이를 반씩 나눔
 */
.history-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: max(640px, calc(var(--screen-h) - var(--topbar-height) - 40px));
}

.cards {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: opacity var(--m-value);
}

/* 조회 중에는 아래 카드들을 흐리게 (화면설계서: 투명도 60%) */
.cards.loading {
  opacity: 0.6;
}

.main {
  display: grid;
  grid-template-columns: minmax(0, 1180fr) minmax(0, 684fr);
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
  grid-template-rows: minmax(0, 1fr);
}

.side {
  position: relative; /* 상세 패널이 이 칸을 꽉 채워 덮음 */
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

/* 오른쪽 그래프 카드 2개가 높이를 반씩 */
.side > :deep(.base-card) {
  flex: 1;
}

.detail {
  position: absolute;
  inset: 0;
  z-index: 5;
}

/* 상세 패널: 오른쪽에서 24px 밀려 들어오며 나타남 (--m-panel 0.24초) */
.panel-enter-active,
.panel-leave-active {
  transition: opacity var(--m-panel), transform var(--m-panel);
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateX(24px);
}

/* 한 열로 쌓이는 좁은 화면: 한 화면에 맞추지 않고 내용 높이대로 (표는 14행 높이 고정) */
@media (max-width: 1400px) {
  .history-page {
    height: auto;
  }

  .main {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: none;
  }

  .main > :deep(.history-card) {
    height: 730px;
  }

  .side > :deep(.base-card) {
    flex: none;
    height: 376px;
  }
}
</style>
