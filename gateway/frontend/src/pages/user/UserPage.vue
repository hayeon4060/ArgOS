<!--
  사용자 관리 화면 (화면설계안 v-rev3.js vUsers(), 화면설계서 G1, API 명세서 G1)
  - 이 화면은 조건을 들고 목록을 불러오고 부품을 이어 주는 일만 함. 그리는 부분은 features/user/
  - 배치 (설계안 1920): 탭 줄 / 왼쪽 380 (권한별 사용자 · 권한별 사용 범위) | 목록 1020 | 오른쪽 패널 448
  - 본사 관리자: 모든 사용자 / 공장 담당자: 소속 공장의 공장·공정 담당자만 (공장 선택 고정)
  - 오른쪽 패널은 기본으로 내 정보. 목록 줄을 누르면 그 사용자, '사용자 추가'를 누르면 입력 폼
  - 입력 중에 다른 줄을 누르면 '저장하지 않았습니다' 확인 창
-->
<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getScopeTree } from '@/api/sites'
import { getUsers } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import SegButtons from '@/components/SegButtons.vue'
import RoleMatrixCard from '@/features/user/RoleMatrixCard.vue'
import RoleSummaryCard from '@/features/user/RoleSummaryCard.vue'
import UserListCard from '@/features/user/UserListCard.vue'
import UserPanel from '@/features/user/UserPanel.vue'
import { ROLE } from '@/features/user/userMeta'

const PER_PAGE = 15 // 한 쪽 행 수 (설계안)

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const actor = computed(() => auth.user)
const isHq = computed(() => auth.role === ROLE.HQ_ADMIN)

/* ───────── 공장 (담당 범위 고르기 · 목록 거르기) ───────── */
const factories = ref([])

/* ───────── 목록 ───────── */
const filter = reactive({ role: '', factoryId: '', q: '', page: 1 })
const list = reactive({ rows: null, total: 0, counts: null, inactiveCount: 0, busy: false })
let seq = 0

/** 처음 목록을 받았는지 — 받기 전에는 화면 대신 뼈대 (실패해도 받은 것으로) */
const ready = ref(false)

async function loadList() {
  const my = ++seq
  list.busy = true
  try {
    const r = await getUsers({ role: filter.role, factoryId: filter.factoryId, q: filter.q.trim(), page: filter.page - 1, size: PER_PAGE })
    if (my !== seq) return
    Object.assign(list, { rows: r.items, total: r.total, counts: r.counts, inactiveCount: r.inactiveCount ?? 0 })
  } catch (error) {
    if (my === seq) toast(error.message, { type: 'err' })
  } finally {
    if (my === seq) {
      list.busy = false
      ready.value = true
    }
  }
}
watch(() => [filter.role, filter.factoryId, filter.page], loadList)

// 검색: 입력이 멈추고 0.3초 뒤 (화면설계서)
let qTimer = 0
function onSearch(e) {
  clearTimeout(qTimer)
  qTimer = setTimeout(() => {
    filter.q = e.target.value
    filter.page = 1
    loadList()
  }, 300)
}

/** 도넛 범례를 누르면 그 권한으로 */
function pickRole(r) {
  filter.role = r
  filter.page = 1
}

/* ───────── 오른쪽 패널 ───────── */
const panel = ref(null)
const selectedId = ref(null)
const flashId = ref(null)
const pending = ref(null) // 입력 중에 다른 곳을 누르면 확인 뒤 할 일

/** 입력 중이면 확인 창, 아니면 바로 */
function guard(action) {
  if (panel.value?.dirty) pending.value = action
  else action()
}
function confirmLeave() {
  const action = pending.value
  pending.value = null
  action()
}

const pickUser = (id) => guard(() => (selectedId.value = id === actor.value.id ? null : id))
const backToMe = () => guard(() => (selectedId.value = null))
const startAdd = () =>
  guard(async () => {
    selectedId.value = null
    await nextTick() // 패널이 '내 정보' 불러오기를 시작한 뒤에 추가 폼으로 (그 불러오기는 버려짐)
    panel.value?.startAdd()
  })

/** 저장 뒤: 목록 다시 불러오고 그 줄 2초 강조 */
async function onChanged(id) {
  await loadList()
  if (id !== actor.value.id) selectedId.value = id
  flashId.value = id
  setTimeout(() => (flashId.value = null), 2000)
}
function onDeleted() {
  selectedId.value = null
  loadList()
}

/* ───────── 탭 줄 ───────── */
const TABS = [
  { value: 'users', label: '사용자 관리' },
  { value: 'logs', label: '이용 로그' },
  { value: 'factory', label: '공장 관리' },
]
const tab = computed({
  get: () => 'users',
  set: (v) => {
    if (v === 'factory') guard(() => router.push({ name: 'factory', params: { siteId: route.params.siteId } }))
    else if (v === 'logs') guard(() => router.push({ name: 'logs', params: { siteId: route.params.siteId } }))
  },
})

/* ───────── 처음 ───────── */
onMounted(async () => {
  try {
    // 공장 담당자는 담당 공장만 옴 (getScopeTree — 실서버 /tree 는 본사 관리자 전용)
    const tree = await getScopeTree(route.params.siteId || 1)
    factories.value = tree.factories.map((f) => ({
      id: f.id,
      name: f.name,
      processes: f.processes.map((p) => ({ id: p.id, name: p.name })),
    }))
    // 공장 담당자는 소속 공장으로 고정
    if (!isHq.value && factories.value[0]) filter.factoryId = String(factories.value[0].id)
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
  loadList()
})
</script>

<template>
  <!-- 처음 들어올 때만 뼈대 (거르기 · 쪽 이동은 목록 카드가 스스로 표시) -->
  <PageSkeleton v-if="!ready" shape="table" layout="user" />

  <div v-else class="user-page">
    <!-- 탭 줄 -->
    <div class="mtab">
      <SegButtons v-model="tab" :options="TABS" />
      <span class="desc">사용자를 조회 · 등록 · 수정 · 삭제</span>
      <label class="search">
        <AppIcon name="search" :size="16" />
        <input type="search" placeholder="이름, 아이디 검색" aria-label="사용자 검색" @input="onSearch" />
      </label>
      <button type="button" class="btn btn-primary add" @click="startAdd"><AppIcon name="plus" :size="16" />사용자 추가</button>
    </div>

    <div class="grid">
      <div class="left">
        <RoleSummaryCard :counts="list.counts" :inactive-count="list.inactiveCount" :is-hq="isHq" :sub="isHq ? '전체 사업장' : factories[0]?.name ?? ''" @pick="pickRole" />
        <RoleMatrixCard />
      </div>

      <UserListCard
        v-model:role="filter.role"
        v-model:factory-id="filter.factoryId"
        v-model:page="filter.page"
        :rows="list.rows"
        :total="list.total"
        :per-page="PER_PAGE"
        :is-hq="isHq"
        :factories="factories"
        :factory-locked="!isHq"
        :selected-id="selectedId"
        :flash-id="flashId"
        :me-id="actor?.id"
        :query="filter.q"
        :busy="list.busy"
        @pick="pickUser"
      />

      <UserPanel
        v-if="actor"
        ref="panel"
        :user-id="selectedId"
        :actor="actor"
        :factories="factories"
        @close="backToMe"
        @changed="onChanged"
        @deleted="onDeleted"
      />
    </div>

    <ConfirmModal :open="!!pending" title="입력한 내용을 저장하지 않았습니다" ok-text="저장하지 않고 나가기" @ok="confirmLeave" @cancel="pending = null">
      저장하지 않고 나가면 입력한 내용이 사라집니다.
    </ConfirmModal>
  </div>
</template>

<style scoped>
/*
 * 배치 (설계안 1920 기준): 왼쪽 380 | 목록 1020 | 패널 448 — 같은 비율로 줄어듦
 * 1600px 이하는 왼쪽 카드를 아래로 보내고 목록 | 패널 두 칸
 */
.user-page {
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

.search {
  width: 260px;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--color-text-weak);
}

.search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 13.5px;
  color: var(--color-text);
}

.add {
  height: 38px;
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 380fr) minmax(0, 1020fr) minmax(0, 448fr);
  gap: 16px;
  align-items: stretch;
  height: max(760px, calc(var(--screen-h) - var(--topbar-height) - 110px));
}

.left {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  overflow-y: auto;
}

@media (max-width: 1600px) {
  .grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 420px);
    height: auto;
  }

  .left {
    grid-column: 1 / -1;
    grid-row: 2;
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: start;
  }
}
</style>
