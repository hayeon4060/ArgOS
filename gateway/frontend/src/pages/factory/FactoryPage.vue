<!--
  공장 관리 화면 — 설비 재배치 · 변경 (화면설계안 v-pages2.js vLayout(), 화면설계서 H1, API 명세서 H1)
  - 이 화면은 범위(사업장 › 공장 › 공정)를 들고 배치도를 불러오고, 바꾼 값을 모아 한 번에 저장하는 일만 함
    그리는 부분은 features/factory/ (설비 목록 · 설비 배치도 · 설비 정보)
  - 배치 (설계안 1920): 탭 줄 / 설비 목록 300 | 설비 배치도 1128 | 설비 정보 420
  - 바꾼 값은 저장 전까지 draft 에만 있음 → '저장'을 누르면 PUT /processes/{id}/layout 한 번
  - 저장하지 않은 변경이 있는데 다른 화면·범위로 가면 확인 창
  - 배치도가 있는 공정은 지금 5공장 도장 공정뿐 (다른 공정은 '등록된 배치도가 없습니다')
  - 2026-11-19 추가 (재원님 요청)
    · 설비 정보의 '설비 기준값 관리' 단추 → 기준값 관리 창 (예전 '설비 기준값 관리' 메뉴·화면을 이 창으로 옮김)
    · (2차) 사용 상태 · 배기팬 자동 제어 스위치는 설비 정보 카드에서 빼고 기준값 관리 창 '설비 설정'으로 옮김
      창에서 바꾸면 PATCH /equipment/{id}/settings 로 바로 저장 → onSettings 가 이 화면의 저장된 값만 맞춤
    · (2차) 설비 상세는 이제 그 화면 위에 같은 창을 띄움 → 주소로 창을 여는 기능(?open=thresholds)은 뺌
  - 2026-11-20 추가 (재원님 요청): 공장 · 공정 추가
    · 공장 드롭다운 맨 아래 '공장 추가'(본사 관리자만), 공정 드롭다운 맨 아래 '공정 추가' → 이름 입력 창 → POST /factories · /processes
    · 공정을 추가하면 바로 그 공정을 조회 → 설비 없는 빈 배치도 (LayoutMapCard 가 설비 0대면 격자 바닥만)
    · 공장 · 공정이 없으면 '아직 공장이 없습니다' · '아직 공정이 없습니다' 안내 + 추가 단추 (사업장 선택에서 막 만든 사업장)
  - 2026-11-20 추가 (재원님 요청): 설비 추가 · 삭제 — 도장 공정도, 새로 만든 공정도 같은 배치도 그림에서
    · 설비 목록 맨 아래 '설비 추가' → 창에서 지금 있는 설비(equipmentCatalog.js)를 고름
      → 도장 공정 그림의 그 설비 그림을 복사해서 놓음 (그림에 그려진 자리, 이미 설비가 있으면 옆으로) — 저장 전까지 created 에 '새 설비'로
    · 설비 정보의 '설비 삭제' → 저장 전까지 deleted 에 '삭제 예정'으로 (배치도에서는 바로 사라짐, '삭제 취소' 가능)
    · '저장'을 누르면 PUT /processes/{id}/layout 의 equipment.create · update · delete 로 한꺼번에
      (서버는 센서가 붙은 설비는 지우지 않고 '사용 안 함'으로 바꿈 → 저장 뒤 안내)
    · 새로 만든 공정은 등록된 설비가 없어서 설비 그림이 모두 숨겨진 빈 바닥으로 보임 (paintLayout.js hiddenDrawings)
-->
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { createFactory } from '@/api/factory'
import { createProcess, getProcessLayout, getProcessStatus, saveProcessLayout } from '@/api/process'
import { getSiteName, getSiteTree, getSites } from '@/api/sites'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import NameInputModal from '@/components/NameInputModal.vue'
import PageSkeleton from '@/components/skeleton/PageSkeleton.vue'
import SegButtons from '@/components/SegButtons.vue'
import EquipmentEditPanel from '@/features/factory/EquipmentEditPanel.vue'
import EquipmentPickerModal from '@/features/factory/EquipmentPickerModal.vue'
import EquipmentListCard from '@/features/factory/EquipmentListCard.vue'
import LayoutMapCard from '@/features/factory/LayoutMapCard.vue'
import ScopeSelect from '@/features/factory/ScopeSelect.vue'
import { setFactoryCount } from '@/features/site/siteSetup'
import ThresholdModal from '@/features/threshold/ThresholdModal.vue'
import { uniqueName } from '@/features/factory/equipmentCatalog'
import { bodyOf, changesOf, currentOf, newEquipment, placeOf } from '@/features/factory/layoutDraft'
import { PART_SHAPES, hiddenDrawings } from '@/features/process/paintLayout'
import { buildProcessEquipment, mergeByKey } from '@/features/process/processEquipment'

// 처음 고르는 범위 (설계안 기본값 울산공장 › 5공장 › 도장 공정 — 지금 배치도가 있는 공정). 없으면 첫 공장·공정
const DEFAULT_FACTORY = '5공장'
const DEFAULT_PROCESS = '도장'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isHq = computed(() => auth.role === ROLE.HQ_ADMIN)

/* ───────── 범위: 사업장 › 공장 › 공정 ───────── */
const sites = ref([])
const tree = ref(null) // GET /sites/{id}/tree
const pick = reactive({ siteId: '', factoryId: '', processId: '' }) // 고르는 중
const scope = reactive({ processId: null, factoryId: null, name: '', path: '' }) // 조회한 범위 (path: '5공장 · 도장 공정')

const factories = computed(() => {
  const all = tree.value?.factories ?? []
  const mine = auth.user?.scope?.factoryIds ?? []
  return isHq.value ? all : all.filter((f) => mine.includes(f.id))
})
const processes = computed(() => factories.value.find((f) => String(f.id) === String(pick.factoryId))?.processes ?? [])

async function loadTree(siteId) {
  tree.value = await getSiteTree(siteId)
  setFactoryCount(siteId, tree.value.factories.length) // 상단 메뉴(설정 전 사업장이면 '관리'만)와 맞춤
  const f = factories.value.find((x) => x.name === DEFAULT_FACTORY) ?? factories.value[0]
  pick.factoryId = f ? String(f.id) : ''
  pickFirstProcess()
}
function pickFirstProcess() {
  const p = processes.value.find((x) => x.name.includes(DEFAULT_PROCESS)) ?? processes.value[0]
  pick.processId = p ? String(p.id) : ''
}
async function onSite() {
  try {
    await loadTree(pick.siteId)
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/* ───────── 배치도 불러오기 ───────── */
const layout = ref(null) // 저장된 배치 (없으면 null)
const status = ref(null)
const loading = ref(true)
const missing = ref(false) // 배치도가 없는 공정
const created = ref([]) // 새 설비(저장 전) — layoutDraft.js newEquipment 모양
const deleted = ref([]) // 지울 설비 id (저장 전)
const draft = reactive({}) // { 설비 id: { pos, name, type, active } } 저장 전 변경 (값 이름은 DB · layout 응답과 같음)
const defaults = ref({}) // 설비 이름(key) → 그림에 그려진 자리 { x, y } (위치 값이 없을 때)
const selectedId = ref(null)

function clearDraft() {
  for (const id of Object.keys(draft)) delete draft[id]
  created.value = []
  deleted.value = []
}

async function load() {
  loading.value = true
  missing.value = false
  try {
    const [l, s] = await Promise.all([getProcessLayout(scope.processId), getProcessStatus(scope.processId)])
    layout.value = l
    status.value = s
  } catch (error) {
    layout.value = null
    status.value = null
    if (error.code === 'NOT_FOUND') missing.value = true
    else toast(error.message, { type: 'err' })
  } finally {
    clearDraft()
    selectedId.value = layout.value?.equipment[0]?.id ?? null
    loading.value = false
  }
}

/**
 * 아직 고를 공장 · 공정이 없을 때 본문 안내: 'factory'(공장 없음) · 'process'(공정 없음) · ''(있음)
 * (막 만든 사업장 · 공장은 여기서 '추가' 단추를 보여 줌)
 */
const needs = ref('')

/** 조회: 고른 범위로 다시 불러오기 (저장 안 한 변경이 있으면 확인 창 먼저) */
function query() {
  guard(runQuery)
}

function runQuery() {
  const f = factories.value.find((x) => String(x.id) === String(pick.factoryId))
  const p = processes.value.find((x) => String(x.id) === String(pick.processId))
  if (!p) {
    // 공장이나 공정이 없음 → 배치도 대신 '추가' 안내
    needs.value = f ? 'process' : 'factory'
    scope.processId = null
    scope.factoryId = f?.id ?? null
    scope.name = f?.name ?? ''
    layout.value = null
    clearDraft()
    loading.value = false
    return
  }
  needs.value = ''
  scope.processId = p.id
  scope.factoryId = f?.id ?? null
  scope.name = `${f?.name ?? ''} ${p.name}`
  scope.path = `${f?.name ?? ''} · ${p.name}`
  load()
}

/* ───────── 공장 · 공정 추가 (2026-11-20) ───────── */
const adding = ref('') // 이름 입력 창: 'factory' | 'process' | ''(닫힘)
const addBusy = ref(false)
const siteName = computed(() => sites.value.find((s) => String(s.id) === String(pick.siteId))?.name ?? '')
const factoryName = computed(() => factories.value.find((f) => String(f.id) === String(pick.factoryId))?.name ?? '')

// 이름 입력 창 글자 (공장 / 공정)
const addForm = computed(() =>
  adding.value === 'factory'
    ? { title: '공장 추가', sub: `${siteName.value} 아래`, label: '공장 이름', placeholder: '예: 1공장', taken: factories.value.map((f) => f.name) }
    : { title: '공정 추가', sub: `${siteName.value} › ${factoryName.value} 아래`, label: '공정 이름', placeholder: '예: 도장 공정', taken: processes.value.map((p) => p.name) },
)

/** 추가 창 열기 (저장 안 한 배치 변경이 있으면 버려도 되는지 먼저 물음 — 추가하면 새 범위를 조회하므로) */
function openAdd(kind) {
  guard(() => (adding.value = kind))
}

/**
 * 이름 입력 창 '추가'
 * - 추가한 뒤 사업장 펼치기를 다시 불러와서(실제 서버 값) 응답의 id 로 고름
 * - 공장: 그 공장을 고르고 '아직 공정이 없습니다' 안내 / 공정: 그 공정을 바로 조회 (빈 배치도)
 */
async function submitAdd(name) {
  addBusy.value = true
  try {
    if (adding.value === 'factory') {
      const made = await createFactory({ siteId: pick.siteId, name })
      tree.value = await getSiteTree(pick.siteId)
      setFactoryCount(pick.siteId, tree.value.factories.length) // 상단 메뉴 탭이 다시 보임
      pick.factoryId = String(made.id)
      pick.processId = ''
      toast(`${name}을(를) 추가했습니다. 공정을 추가해 주세요.`)
    } else {
      const made = await createProcess({ factoryId: pick.factoryId, name })
      tree.value = await getSiteTree(pick.siteId)
      pick.processId = String(made.id)
      toast(`${name}을(를) 추가했습니다. 설비를 배치할 수 있습니다.`)
    }
    adding.value = ''
    runQuery() // 저장 안 한 변경 확인은 openAdd 에서 이미 거침
  } catch (error) {
    toast(error.message, { type: 'err' }) // 409 CODE_TAKEN: 같은 이름이나 코드
  } finally {
    addBusy.value = false
  }
}

/* ───────── 화면용 설비 (저장값 + 지금 값 + 고친 값) ───────── */
// 저장된 설비 + 배치도 안 이름(key) · 그림 이름(shape) · 센서 (변경 목록 계산용)
const saved = computed(() => {
  const built = buildProcessEquipment(layout.value, status.value)
  return (layout.value?.equipment ?? []).map((e) => {
    const b = built.find((x) => x.id === e.id)
    return { ...e, key: b.key, shape: b.shape, level: b.level, sensors: b.sensors }
  })
})
// 지금 화면에 보이는 값 (고친 값 반영, 지울 설비는 deleting 표시) + 새 설비(저장 전)는 맨 뒤에
const shown = computed(() => [
  ...saved.value.map((e) => ({ ...e, ...currentOf(e, draft), deleting: deleted.value.includes(e.id) })),
  ...created.value,
])
const changes = computed(() => changesOf(saved.value, draft, defaults.value, created.value, deleted.value))

const selected = computed(() => shown.value.find((e) => e.id === selectedId.value) ?? null)
// 고른 설비의 지금 위치 (위치 값이 없으면 그림에 그려진 자리)
const selectedPlace = computed(() => (selected.value ? placeOf(selected.value.pos, selected.value.key, defaults.value) : null))
const listRows = computed(() => shown.value.map((e) => ({ ...e, changed: changes.value.some((c) => c.id === e.id) })))

// 배치도에 그릴 설비: 지울 설비는 바로 뺌
const onMap = computed(() => shown.value.filter((e) => !e.deleting))
const mapEquipment = computed(() => onMap.value.map(({ key, shape, name, level, sensors, seq }) => ({ key, shape, name, level, sensors, seq })))
// 그림 하나에 설비가 여러 대(실제 서버: 건조로 + 배기팬 + 버너실)면 대표 설비 값만 그림 위치 · 사용 여부에 씀
const shownMain = computed(() => mergeByKey(onMap.value))
// 배치도에 그릴 위치: 저장된(또는 고친) pos 가 있는 설비만 (없으면 그림 그대로)
const mapPositions = computed(() => Object.fromEntries(shownMain.value.filter((e) => e.pos).map((e) => [e.key, e.pos])))
const mapInactive = computed(() => shownMain.value.filter((e) => !e.active).map((e) => e.key))
// 숨길 설비 그림: 등록되지 않은 설비 (지운 설비, 새로 만든 공정은 전부)
const mapHidden = computed(() => hiddenDrawings(onMap.value))

/* ───────── 고치기 ───────── */
/** 설비 하나의 값 하나 고치기 (저장값과 같아지면 지움, 새 설비는 그 줄을 바로 고침) */
function setField(id, field, value) {
  const fresh = created.value.find((c) => c.id === id)
  if (fresh) {
    fresh[field] = value
    return
  }
  const eq = saved.value.find((e) => e.id === id)
  if (!eq) return
  const next = { ...draft[id], [field]: value }
  const was = currentOf(eq, {})
  if (JSON.stringify(was[field]) === JSON.stringify(value)) delete next[field]
  if (Object.keys(next).length) draft[id] = next
  else delete draft[id]
}

const idOf = (key) => shownMain.value.find((e) => e.key === key)?.id // 그림을 끌면 대표 설비를 옮김
const onMapSelect = (key) => (selectedId.value = idOf(key) ?? selectedId.value)
const onMove = (key, pos) => setField(idOf(key), 'pos', pos)
const onUse = (on) => selected.value && setField(selected.value.id, 'active', on)
const onPanel = (field, value) => setField(selectedId.value, field, value)

/* ───────── 설비 기준값 관리 창 ───────── */
const thresholdOpen = ref(false)

/**
 * 창에서 사용 상태를 바꾸면(바로 저장됨) 이 화면의 저장된 값만 맞춤
 * - 다시 불러오지 않음: 저장 전 배치 변경(draft)을 지키려고
 * - 배기팬 자동 제어는 이 화면에서 쓰지 않으므로 맞출 것 없음
 */
function onSettings({ active }) {
  const row = layout.value?.equipment.find((e) => e.id === selectedId.value)
  if (!row) return
  row.active = active
  if (draft[row.id] && 'active' in draft[row.id]) setField(row.id, 'active', draft[row.id].active) // 같아졌으면 변경 줄에서 빠짐
}

/* ───────── 설비 추가 · 삭제 (2026-11-20) ───────── */
const pickerOpen = ref(false)
let tempSeq = 0 // 새 설비 임시 id n1, n2 …
// 자리가 겹칠 때 옆으로 비키는 거리: 그림 격자 두 칸 (PaintMap GU × 2)
const STEP = { x: 124.9, y: 39.1 }
// 부품 그림(배기팬 · 버너실 · 펌프 · 열화상 카메라)은 그림에서 건조로 위 · 옆에 붙어 있음 → 겹쳐 보이지 않게 앞쪽 바닥으로 (격자 GV × 4)
const PART_FRONT = { x: -73.6, y: 97 }

/**
 * 설비 추가 창에서 고른 설비들 → 새 설비로 놓기
 * - 자리: 도장 공정 그림에서 그 설비가 그려진 자리(defaults). 거기에 이미 설비가 있으면 옆으로 비켜 놓음
 * - 같은 이름이 있으면 뒤에 번호 (상도 건조로 2)
 */
function onPick(items) {
  pickerOpen.value = false
  const taken = shown.value.map((e) => e.name)
  const spots = onMap.value.map((e) => placeOf(e.pos, e.key, defaults.value)).filter(Boolean)
  const busy = (p) => spots.some((s) => Math.abs(s.x - p.x) < 30 && Math.abs(s.y - p.y) < 30)
  for (const item of items) {
    let pos = { ...(defaults.value[item.shape] ?? { x: 300, y: 500 }) }
    if (PART_SHAPES[item.shape]) pos = { x: Math.round(pos.x + PART_FRONT.x), y: Math.round(pos.y + PART_FRONT.y) }
    while (busy(pos)) pos = { x: Math.round((pos.x + STEP.x) * 10) / 10, y: Math.round((pos.y + STEP.y) * 10) / 10 }
    spots.push(pos)
    const name = uniqueName(item.name, taken)
    taken.push(name)
    tempSeq += 1
    created.value.push(newEquipment(`n${tempSeq}`, { ...item, name }, pos))
  }
  selectedId.value = created.value.at(-1)?.id ?? selectedId.value
  toast(`설비 ${items.length}대를 놓았습니다. 끌어서 자리를 잡고 '저장'을 누르세요.`)
}

/**
 * 설비 삭제 · 삭제 취소 (설비 정보의 단추, 설비 목록의 ×)
 * - 새 설비(저장 전)는 바로 뺌, 저장된 설비는 '삭제 예정'으로 두었다가 저장할 때 지움
 */
function toggleRemove(id) {
  if (created.value.some((c) => c.id === id)) {
    created.value = created.value.filter((c) => c.id !== id)
    if (selectedId.value === id) selectedId.value = onMap.value[0]?.id ?? null
    return
  }
  deleted.value = deleted.value.includes(id) ? deleted.value.filter((x) => x !== id) : [...deleted.value, id]
}

/** 되돌리기: 저장 전 변경을 모두 버림 */
function undo() {
  clearDraft()
  toast('저장하기 전 배치로 되돌렸습니다.', { type: 'info' })
}

/* ───────── 저장 (한 번에) ───────── */
const saving = ref(false)
async function save() {
  const n = changes.value.length
  if (!n || saving.value) return
  saving.value = true
  try {
    const removing = [...deleted.value]
    const res = await saveProcessLayout(scope.processId, bodyOf(layout.value.version, saved.value, draft, created.value, removing))
    // 고른 설비가 새 설비였으면 응답의 실제 id 로 (created.equipment: { tempId: id })
    const keep = res?.created?.equipment?.[selectedId.value] ?? selectedId.value
    await load()
    if (shown.value.some((e) => e.id === keep)) selectedId.value = keep
    toast(`변경 ${n}건을 저장했습니다. 대시보드 배치도에 바로 반영됩니다.`)
    // 서버는 센서가 붙은 설비는 지우지 않고 '사용 안 함'으로 바꿈 → 남아 있으면 알려 줌
    const kept = removing.filter((id) => saved.value.some((e) => e.id === id)).length
    if (kept) toast(`센서가 있는 설비 ${kept}대는 지우지 않고 '사용 안 함'으로 바꿨습니다.`, { type: 'info', ms: 6000 })
  } catch (error) {
    // 409: 다른 사람이 먼저 저장함 → 바꾼 값은 그대로 두고 안내 (다시 불러오면 사라짐)
    toast(error.message, { type: 'err', ms: error.status === 409 ? 8000 : 3400 })
  } finally {
    saving.value = false
  }
}

/* ───────── 저장하지 않고 나가기 확인 ───────── */
const pending = ref(null)
let leaving = false

function guard(action) {
  if (changes.value.length) pending.value = action
  else action()
}
function confirmLeave() {
  const action = pending.value
  pending.value = null
  action()
}
onBeforeRouteLeave((to) => {
  if (leaving || !changes.value.length) return true
  pending.value = () => {
    leaving = true
    router.push(to.fullPath)
  }
  return false
})

/* ───────── 탭 줄 ───────── */
const TABS = [
  { value: 'users', label: '사용자 관리' },
  { value: 'logs', label: '이용 로그' },
  { value: 'factory', label: '공장 관리' },
]
const tab = computed({
  get: () => 'factory',
  set: (v) => {
    if (v === 'users') router.push({ name: 'user', params: { siteId: route.params.siteId } })
    else if (v === 'logs') router.push({ name: 'logs', params: { siteId: route.params.siteId } }) // 저장 안 한 변경은 onBeforeRouteLeave 가 확인
  },
})

/* ───────── 처음 ───────── */
watch(
  () => route.params.siteId,
  async (siteId) => {
    if (!siteId) return
    try {
      // 본사 관리자는 사업장을 고를 수 있고, 공장 담당자는 담당 사업장 하나로 고정
      sites.value = isHq.value
        ? (await getSites()).map((s) => ({ id: s.id, name: s.name }))
        : [{ id: Number(siteId), name: await getSiteName(siteId) }]
      pick.siteId = String(siteId)
      await loadTree(siteId)
    } catch (error) {
      toast(error.message, { type: 'err' })
    }
    query()
  },
  { immediate: true },
)
</script>

<template>
  <div class="factory-page">
    <!-- 탭 줄: 탭 / 범위 고르기 + 조회 / 저장하지 않은 변경 · 되돌리기 · 저장 -->
    <div class="mtab">
      <SegButtons v-model="tab" :options="TABS" />
      <span class="scope">
        <ScopeSelect v-model="pick.siteId" :options="sites" label="사업장" :disabled="!isHq" @change="onSite" />
        <AppIcon name="cr" :size="14" class="sep" />
        <!-- 공장 추가는 본사 관리자만 (공장 담당자는 담당 공장이 고정) -->
        <ScopeSelect
          v-model="pick.factoryId"
          :options="factories"
          label="공장"
          add-label="공장 추가"
          :can-add="isHq"
          :disabled="!isHq"
          @change="pickFirstProcess"
          @add="openAdd('factory')"
        />
        <AppIcon name="cr" :size="14" class="sep" />
        <ScopeSelect
          v-model="pick.processId"
          :options="processes"
          label="공정"
          add-label="공정 추가"
          :can-add="!!pick.factoryId"
          placeholder="공장을 먼저 추가"
          @add="openAdd('process')"
        />
        <button type="button" class="btn btn-primary qry" @click="query"><AppIcon name="search" :size="16" />조회</button>
      </span>

      <span class="acts">
        <Transition name="fade">
          <span v-if="changes.length" class="dirty">저장하지 않은 변경 {{ changes.length }}건</span>
        </Transition>
        <button type="button" class="btn" :disabled="!changes.length || saving" @click="undo"><AppIcon name="undo" :size="16" />되돌리기</button>
        <button type="button" class="btn" :class="{ 'btn-primary': changes.length }" :disabled="!changes.length || saving" @click="save">
          <span v-if="saving" class="m-spin" /><AppIcon v-else name="save" :size="16" />{{ saving ? '저장 중' : '저장' }}
        </button>
      </span>
    </div>

    <!-- 처음 들어올 때 · '조회'로 범위를 바꿀 때: 위 탭 줄은 그대로 두고 아래 본문 칸만 뼈대 -->
    <PageSkeleton v-if="loading" shape="table" layout="factory" body />

    <!-- 고를 공장 · 공정이 아직 없음 (막 만든 사업장 · 공장) -->
    <div v-else-if="needs" class="none base-card">
      <AppIcon :name="needs === 'factory' ? 'factory' : 'layers'" :size="30" />
      <b>{{ needs === 'factory' ? '아직 공장이 없습니다' : '아직 공정이 없습니다' }}</b>
      <span>{{ needs === 'factory' ? siteName : `${siteName} › ${scope.name}` }}</span>
      <button v-if="needs === 'process' || isHq" type="button" class="btn btn-primary add" @click="openAdd(needs)">
        <AppIcon name="plus" :size="16" />{{ needs === 'factory' ? '공장 추가' : '공정 추가' }}
      </button>
    </div>

    <!-- 배치도가 없는 공정 -->
    <div v-else-if="!layout" class="none base-card">
      <AppIcon name="layers" :size="30" />
      <b>등록된 배치도가 없습니다</b>
      <span>{{ scope.name }}</span>
      <small v-if="missing">배치도 올리기는 아직 지원하지 않습니다. 지금은 5공장 도장 공정만 배치를 바꿀 수 있습니다.</small>
    </div>

    <div v-else class="grid">
      <EquipmentListCard
        :rows="listRows"
        :selected-id="selectedId"
        :sub="`${layout.name} · ${listRows.length}대`"
        @select="selectedId = $event"
        @add="pickerOpen = true"
        @remove="toggleRemove"
      />
      <LayoutMapCard
        :equipment="mapEquipment"
        :positions="mapPositions"
        :inactive="mapInactive"
        :selected-key="selected?.key ?? ''"
        :selected="selected"
        :hidden="mapHidden"
        :sub="layout.name"
        @select="onMapSelect"
        @add="pickerOpen = true"
        @move="onMove"
        @use="onUse"
        @defaults="defaults = $event"
      />
      <EquipmentEditPanel
        :eq="selected"
        :place="selectedPlace"
        :changes="changes.filter((c) => c.id === selectedId)"
        @change="onPanel"
        @remove="toggleRemove(selectedId)"
        @thresholds="thresholdOpen = true"
      />
    </div>

    <!-- 설비 기준값 관리 창 (고른 설비 하나) -->
    <ThresholdModal
      v-if="thresholdOpen && selected"
      :equipment-id="selected.id"
      :factory-id="scope.factoryId ?? 5"
      :path="scope.path"
      @close="thresholdOpen = false"
      @settings="onSettings"
    />

    <!-- 설비 추가 창 (지금 있는 설비 목록에서 고르기) -->
    <EquipmentPickerModal :open="pickerOpen" :path="scope.path" @pick="onPick" @cancel="pickerOpen = false" />

    <!-- 공장 · 공정 이름 입력 창 -->
    <NameInputModal
      :open="!!adding"
      :title="addForm.title"
      :sub="addForm.sub"
      :label="addForm.label"
      :placeholder="addForm.placeholder"
      :taken="addForm.taken"
      :busy="addBusy"
      @submit="submitAdd"
      @cancel="adding = ''"
    />

    <ConfirmModal :open="!!pending" title="저장하지 않은 변경이 있습니다" ok-text="저장하지 않고 나가기" @ok="confirmLeave" @cancel="pending = null">
      저장하지 않고 나가면 바꾼 배치가 사라집니다.
    </ConfirmModal>
  </div>
</template>

<style scoped>
/*
 * 배치 (설계안 1920 기준): 목록 300 | 배치도 1128 | 정보 420 — 같은 비율로 줄어듦
 * 1600px 이하는 목록을 아래로 보내고 배치도 | 정보 두 칸
 */
.factory-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mtab {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}

.scope {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sep {
  color: var(--color-text-weak);
}

.qry {
  height: 36px;
  margin-left: 4px;
}

.acts {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.acts .btn {
  height: 38px;
}

.dirty {
  margin-right: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-level-warn-text);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 300fr) minmax(0, 1128fr) minmax(0, 420fr);
  gap: 16px;
  align-items: stretch;
  height: max(760px, calc(var(--screen-h) - var(--topbar-height) - 110px));
}

.grid > * {
  min-height: 0;
}

.none {
  height: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-weak);
  text-align: center;
  animation: softin 240ms both;
}

.none b {
  font-size: 16px;
  color: var(--color-text);
}

.none span {
  font-size: 14px;
  color: var(--color-text-sub);
}

.none small {
  font-size: 13px;
}

.none .add {
  height: 40px;
  margin-top: 10px;
  padding: 0 18px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 200ms;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 1600px) {
  .grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 380px);
    height: auto;
  }

  .grid > :first-child {
    grid-column: 1 / -1;
    grid-row: 2;
    max-height: 420px;
  }

  .grid > :nth-child(3) {
    height: 760px;
  }
}
</style>
