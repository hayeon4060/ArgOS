<!--
  이용 로그 화면 (화면설계안 v-rev3.js vLogs(), 화면설계서 G2, API 명세서 G2)
  - 이 화면은 조건을 들고 로그를 불러오고 부품을 이어 주는 일만 함. 그리는 부분은 features/log/
  - 배치 (설계안 1920): 탭 줄 / 조건 줄 / 이용 로그 표 1420 | 오른쪽 444 (유형별 건수 · 시간대별 이용)
  - 처음: 최근 7일, 전체 (공장 담당자는 소속 공장 고정, 본사 관리자의 기록은 서버가 빼서 줌)
  - 기간 · 공장 · 공정 · 유형을 바꾸면 바로 다시 조회 (설계안), 검색은 입력이 멈추고 0.3초 뒤
  - CSV 로 내보내기: 지금 조건 그대로 (GET /logs/export) → 파일 저장 + 알림 띠
-->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { serverNow } from '@/api/clock'
import { exportLogs, getLogs } from '@/api/logs'
import { getSiteTree } from '@/api/sites'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import SegButtons from '@/components/SegButtons.vue'
import LogFilterBar from '@/features/log/LogFilterBar.vue'
import LogHourCard from '@/features/log/LogHourCard.vue'
import LogTableCard from '@/features/log/LogTableCard.vue'
import LogTypeCard from '@/features/log/LogTypeCard.vue'
import { LOG_GROUPS, PERIODS, rangeOf } from '@/features/log/logMeta'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import { toast } from '@/utils/toast'

const PER_PAGE = 16 // 한 쪽 행 수 (설계안)

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isHq = computed(() => auth.role === ROLE.HQ_ADMIN)

/* ───────── 조건 ───────── */
const filter = reactive({ period: '7d', factoryId: '', processId: '', group: '', q: '', page: 1 })
const range = computed(() => rangeOf(filter.period, serverNow()))
const periodSub = computed(() => PERIODS.find((p) => p.value === filter.period)?.sub ?? '')

/** 서버에 보낼 조건 (유형 칸 → type 코드 쉼표로) */
const criteria = () => ({
  ...range.value,
  factoryId: filter.factoryId,
  processId: filter.processId,
  type: LOG_GROUPS.find((g) => g.key === filter.group)?.types.join(',') ?? '',
  q: filter.q.trim(),
})

/* ───────── 공장 (조건 줄 고르기) ───────── */
const factories = ref([])

/* ───────── 불러오기 ───────── */
const data = reactive({ rows: null, total: 0, countsByType: {}, byHour: [], topUsers: [] })
const busy = ref(false)
const loadKey = ref(0)
let seq = 0

/** 처음 로그를 받았는지 — 받기 전에는 화면 대신 뼈대 (실패해도 받은 것으로) */
const ready = ref(false)

async function load() {
  const my = ++seq
  busy.value = true
  try {
    const r = await getLogs({ ...criteria(), page: filter.page - 1, size: PER_PAGE })
    if (my !== seq) return
    Object.assign(data, { rows: r.items, total: r.total, countsByType: r.countsByType ?? {}, byHour: r.byHour ?? [], topUsers: r.topUsers ?? [] })
    loadKey.value++
  } catch (error) {
    if (my === seq) toast(error.message, { type: 'err' })
  } finally {
    if (my === seq) {
      busy.value = false
      ready.value = true
    }
  }
}

// 조건이 바뀌면 첫 쪽부터 다시, 쪽만 바뀌면 그 쪽
watch(
  () => [filter.period, filter.factoryId, filter.processId, filter.group],
  () => {
    if (filter.page !== 1) filter.page = 1 // 아래 page watch 가 불러옴
    else load()
  },
)
watch(() => filter.page, load)

function onSearch(q) {
  filter.q = q
  if (filter.page !== 1) filter.page = 1
  else load()
}

/* ───────── CSV 로 내보내기 ───────── */
const exporting = ref(false)

/** 파일 이름: ArgOS_이용로그_5공장_도장공정_20261111-20261117.csv (화면설계서 G2) */
function fileName() {
  const f = factories.value.find((x) => String(x.id) === filter.factoryId)
  const p = f?.processes.find((x) => String(x.id) === filter.processId)
  const where = [f?.name ?? '전체', p?.name.replace(/ /g, '')].filter(Boolean).join('_')
  return `ArgOS_이용로그_${where}_${range.value.from.replace(/-/g, '')}-${range.value.to.replace(/-/g, '')}.csv`
}

async function onExport() {
  if (!data.total) {
    toast('내보낼 기록이 없습니다.', { type: 'info' })
    return
  }
  exporting.value = true
  try {
    const { blob, count } = await exportLogs(criteria())
    const name = fileName()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast(`${name} 파일을 저장했습니다.${count != null ? ` (${count}건)` : ''}`)
    load() // 내려받은 것도 로그에 남으므로 다시
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    exporting.value = false
  }
}

/* ───────── 탭 줄 ───────── */
const TABS = [
  { value: 'users', label: '사용자 관리' },
  { value: 'logs', label: '이용 로그' },
  { value: 'factory', label: '공장 관리' },
]
const tab = computed({
  get: () => 'logs',
  set: (v) => {
    if (v === 'users') router.push({ name: 'user', params: { siteId: route.params.siteId } })
    else if (v === 'factory') router.push({ name: 'factory', params: { siteId: route.params.siteId } })
  },
})

/* ───────── 처음 ───────── */
onMounted(async () => {
  try {
    const tree = await getSiteTree(route.params.siteId || 1)
    const mine = auth.user?.scope?.factoryIds ?? []
    factories.value = (isHq.value ? tree.factories : tree.factories.filter((f) => mine.includes(f.id))).map((f) => ({
      id: f.id,
      name: f.name,
      processes: f.processes.map((p) => ({ id: p.id, name: p.name })),
    }))
    // 공장 담당자는 소속 공장으로 고정 (조건이 바뀌어 watch 가 불러옴)
    if (!isHq.value && factories.value[0]) {
      filter.factoryId = String(factories.value[0].id)
      return
    }
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
  load()
})
</script>

<template>
  <!-- 처음 들어올 때만 뼈대 (다시 조회할 때는 표 카드가 스스로 표시) -->
  <PageSkeleton v-if="!ready" shape="table" layout="logs" />

  <div v-else class="log-page">
    <!-- 탭 줄 -->
    <div class="mtab">
      <SegButtons v-model="tab" :options="TABS" />
      <span class="desc">누가 언제 무엇을 했는지 기록</span>
    </div>

    <LogFilterBar
      v-model:period="filter.period"
      v-model:factory-id="filter.factoryId"
      v-model:process-id="filter.processId"
      v-model:group="filter.group"
      :factories="factories"
      :lock-factory="!isHq"
      :range="range"
      :busy="busy"
      :exporting="exporting"
      @search="onSearch"
      @query="load"
      @export="onExport"
    />

    <div class="grid">
      <LogTableCard v-model:page="filter.page" :rows="data.rows" :total="data.total" :per-page="PER_PAGE" :busy="busy" :load-key="loadKey" />
      <div class="right">
        <LogTypeCard :counts="data.countsByType" :group="filter.group" :sub="periodSub" @pick="filter.group = $event" />
        <LogHourCard :by-hour="data.byHour" :top-users="data.topUsers" />
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
 * 배치 (설계안 1920 기준): 표 1420 | 오른쪽 444 — 같은 비율로 줄어듦
 * 1500px 이하는 오른쪽 카드를 표 아래로 두 칸
 */
.log-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mtab {
  display: flex;
  align-items: center;
  gap: 14px;
}

.desc {
  font-size: 13px;
  color: var(--color-text-weak);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1420fr) minmax(0, 444fr);
  gap: 16px;
  align-items: start;
}

.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

@media (max-width: 1500px) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .right {
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: start;
  }
}
</style>
