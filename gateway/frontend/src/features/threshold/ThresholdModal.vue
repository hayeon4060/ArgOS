<!--
  설비 기준값 관리 창 (2026-11-19 재원님 요청: '설비 기준값 관리' 메뉴·화면을 없애고 공장 관리 안의 창으로 옮김)
  - 여는 곳: 공장 관리 › 설비 정보 › '설비 기준값 관리' 단추, 설비 상세 제목 줄 '설비 기준값 관리' 단추·이벤트 '기준값 관리에서 보기'
    (설비 상세에서는 화면 이동 없이 그 화면 위에 뜸)
  - 위쪽 '설비 설정': 사용 상태 · 배기팬 자동 제어 스위치 (2026-11-19 공장 관리 설비 정보에서 옮김) — 누르면 바로 저장
    PATCH /equipment/{id}/settings, 저장되면 settings 이벤트({ active, fanAuto })로 부모에 알림
  - 기능은 예전 화면(pages/threshold/ThresholdPage.vue, 화면설계서 F1)과 같음. 왼쪽 설비 트리만 빠짐 (설비는 공장 관리에서 고름)
    머리(저장·되돌리기·적용 범위) · 계절 · 센서별 카드 3개 · 변경 시뮬레이션 | 변경 이력
  - 데이터: GET /equipment/tree(설비 이름·단계, 건조로 3대 묶음), /equipment/{id}/thresholds, /thresholds/history,
            /sensors/series(7일), POST /thresholds/simulate (입력이 멈추고 0.4초 뒤), PUT /equipment/{id}/thresholds,
            PATCH /equipment/{id}/settings (설비 설정)
  - 저장하지 않은 값이 있는데 닫거나 계절을 바꾸면 확인 창
  - 저장하면 예시 데이터에도 남아 공정·설비 상세의 단계·게이지 기준선이 같이 바뀜 (지금 계절일 때)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { serverNow } from '@/api/clock'
import { updateEquipmentSettings } from '@/api/equipment'
import { getEquipmentTree, getSensorWeek, getThresholdHistory, getThresholds, simulateThreshold } from '@/api/thresholds'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import EquipmentSettingsCard from './EquipmentSettingsCard.vue'
import HistoryCard from './HistoryCard.vue'
import SaveSummary from './SaveSummary.vue'
import SeasonTabs from './SeasonTabs.vue'
import SensorThresholdCard from './SensorThresholdCard.vue'
import SimulationCard from './SimulationCard.vue'
import ThresholdHeader from './ThresholdHeader.vue'
import { saveAll } from './saveFlow'
import { SEASON_META, changes as changesOf, check, draftsOf, num, savedOf } from './thresholdForm'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABEL, SENSOR_TYPES } from '@/types/codes'
import { formatYmd } from '@/utils/format'
import { toast } from '@/utils/toast'

const props = defineProps({
  /** 기준값을 볼 설비 id (공장 관리에서 고른 설비) */
  equipmentId: { type: Number, required: true },
  /** 설비가 있는 공장 id (GET /equipment/tree?factoryId=) */
  factoryId: { type: Number, required: true },
  /** 위치 글자 '5공장 · 도장 공정' */
  path: { type: String, default: '' },
})

const emit = defineEmits(['close', 'settings'])

const auth = useAuthStore()

const tree = ref([])
const thresholds = ref(null) // GET /equipment/{id}/thresholds
const season = ref('WINTER')
const drafts = ref({}) // { 센서 id: { warn: '21.6', alarm: '27' } }
const focus = ref(null) // { sensorId, field } 시뮬레이션·이력 그래프가 보는 칸
const scope = ref('one')
const loading = ref(true)
const errorMessage = ref('')
const shown = ref(true) // false 가 되면 사라지는 움직임 뒤 close
// 창 너비: 1600px, 화면이 좁으면 화면 폭에 맞춤 (양옆 24px 여백)
const modalWidth = Math.min(1600, window.innerWidth - 48)

// 트리에서 이 설비 줄 (이름·종류·단계) 과 같은 공정 (건조로 3대 묶어 저장할 때 씀)
const equipmentRow = computed(() => tree.value.flatMap((p) => p.equipment).find((e) => e.id === props.equipmentId) ?? null)
const processOf = computed(() => tree.value.find((p) => p.equipment.some((e) => e.id === props.equipmentId)))
const sensors = computed(() => thresholds.value?.sensors ?? [])

/* ───────── 불러오기 ───────── */
async function loadTree() {
  tree.value = await getEquipmentTree(props.factoryId)
}

/** 설비 기준값 (계절은 지금 계절로, keepSeason 이면 그대로) */
async function loadEquipment({ keepSeason = false } = {}) {
  const t = await getThresholds(props.equipmentId)
  thresholds.value = t
  if (!keepSeason) season.value = t.activeSeason
  drafts.value = draftsOf(t.sensors, season.value)
  // 처음 보는 칸: 전류 → 온도 → CO 순서의 첫 센서 주의
  const first = SENSOR_TYPES.map((x) => t.sensors.find((s) => s.type === x.type)).find(Boolean)
  focus.value = first ? { sensorId: first.sensorId, field: 'warn' } : null
  scope.value = 'one'
  await loadHistory()
}

async function init() {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadTree()
    await loadEquipment()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

/* ───────── 설비 설정 (사용 상태 · 배기팬 자동 제어) — 누르면 바로 저장 ───────── */
/** 받침에 맞는 '을/를' ('상도 건조로를', '도료 혼합실을') */
const eulReul = (word) => {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  return code >= 0 && code <= 11171 && code % 28 ? '을' : '를'
}

const settingBusy = ref('') // 저장 중인 필드

async function onToggle(field, value) {
  settingBusy.value = field
  try {
    const saved = await updateEquipmentSettings(props.equipmentId, { [field]: value })
    thresholds.value = { ...thresholds.value, active: saved.active, fanAuto: saved.fanAuto }
    emit('settings', { active: saved.active, fanAuto: saved.fanAuto })
    const name = equipmentRow.value?.name ?? '설비'
    if (field === 'active') toast(`${name}${eulReul(name)} ${value ? '사용 중' : '사용 안 함'}으로 바꿨습니다.`)
    else toast(`${name} 배기팬을 ${value ? '자동' : '수동'}으로 바꿨습니다.`)
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    settingBusy.value = ''
  }
}

/* ───────── 변경 이력 ───────── */
// 오늘: 한국 시각 기준 날짜 (서버 시계 기준 — 이용 로그 · 상단 시계와 같은 방식)
const today = formatYmd(serverNow())
const historyRange = ref({ from: '2026-08-01', to: today })
const history = ref([])
const historyBusy = ref(false)

async function loadHistory() {
  historyBusy.value = true
  try {
    history.value = await getThresholdHistory({ equipmentId: props.equipmentId, ...historyRange.value })
  } finally {
    historyBusy.value = false
  }
}

function onHistoryQuery(range) {
  if (!range) {
    toast('시작일이 종료일보다 늦습니다.', { type: 'warn' })
    return
  }
  historyRange.value = range
  loadHistory()
}

/* ───────── 입력 ───────── */
const changes = computed(() => changesOf(sensors.value, drafts.value, season.value))
const hasError = computed(() => sensors.value.some((s) => check(s, drafts.value[s.sensorId])?.level === 'err'))
const canSave = computed(() => changes.value.length > 0 && !hasError.value)

function onInput(sensor, field, value) {
  drafts.value = { ...drafts.value, [sensor.sensorId]: { ...drafts.value[sensor.sensorId], [field]: value } }
  focus.value = { sensorId: sensor.sensorId, field }
}

function undo() {
  drafts.value = draftsOf(sensors.value, season.value)
  toast('바꾸기 전 값으로 되돌렸습니다.', { type: 'info' })
}

/** 종류별 센서 (없으면 undefined → 점선 칸) */
const sensorOf = (type) => sensors.value.find((s) => s.type === type)

/* ───────── 바꾼 값이 있을 때 계절 바꾸기 · 창 닫기 확인 ───────── */
const pending = ref(null) // 확인 뒤 할 일 { what: '계절을 바꾸면' | '창을 닫으면', action }
function guard(what, action) {
  if (changes.value.length) pending.value = { what, action }
  else action()
}
function confirmDiscard() {
  const { action } = pending.value
  pending.value = null
  action()
}

function selectSeason(s) {
  if (s === season.value) return
  guard('계절을 바꾸면', () => {
    season.value = s
    drafts.value = draftsOf(sensors.value, s)
  })
}

/** 닫기 (X · 바깥 누르기 · Esc) — 저장 안 한 값이 있으면 확인 */
function requestClose() {
  if (confirmOpen.value || pending.value) return // 확인 창이 떠 있을 때 Esc 는 확인 창만 닫음
  guard('창을 닫으면', () => (shown.value = false))
}

/* ───────── 변경 시뮬레이션 (7일 그래프 + 알림 수) ───────── */
const focusSensor = computed(() => sensors.value.find((s) => s.sensorId === focus.value?.sensorId) ?? null)
const weekCache = ref({})
const sim = ref(null)
const simBusy = ref(false)

/** 지금 칸의 입력 값 (숫자가 아니면 저장된 값) */
const nextOf = (s) => {
  const d = drafts.value[s.sensorId]
  const saved = savedOf(s, season.value)
  return { warn: num(d?.warn) ?? saved.warn, alarm: num(d?.alarm) ?? saved.alarm }
}

let simTimer = 0
watch(
  [focusSensor, drafts, season],
  ([s]) => {
    if (!s) return
    clearTimeout(simTimer)
    simBusy.value = true
    // 입력이 멈추고 0.4초 뒤에 부름 (명세서 F1)
    simTimer = setTimeout(async () => {
      try {
        if (!weekCache.value[s.sensorId]) weekCache.value = { ...weekCache.value, [s.sensorId]: await getSensorWeek(s.sensorId) }
        const n = nextOf(s)
        // 하한 기준(전류)은 화면에서 안 바꾸므로 저장된 값 그대로 — 빼면 서버가 하한 없이 셈
        const saved = savedOf(s, season.value)
        sim.value = await simulateThreshold({ sensorId: s.sensorId, warn: n.warn, alarm: n.alarm, lowWarn: saved.lowWarn ?? null, lowAlarm: saved.lowAlarm ?? null })
      } finally {
        simBusy.value = false
      }
    }, 400)
  },
  { deep: true },
)

/* ───────── 저장 ───────── */
const saving = ref(false)
const confirmOpen = ref(false)
const reason = ref('')
const userLabel = computed(() => `${ROLE_LABEL[auth.role] ?? ''} ${auth.user?.name ?? ''}`.trim())
const ovenIds = computed(() => processOf.value?.equipment.filter((e) => e.type === 'OVEN').map((e) => e.id) ?? [])
const validFrom = computed(() => sensors.value[0]?.seasons[season.value]?.validFrom ?? '')

/** 저장 단추 → 확인 창 (사유 칸은 매번 비워서 엶) */
function openConfirm() {
  reason.value = ''
  confirmOpen.value = true
}

/** 설비 id → 이름 (트리에서 찾음) */
const equipmentName = (id) => processOf.value?.equipment.find((e) => e.id === id)?.name ?? `설비 ${id}`

async function save() {
  saving.value = true
  try {
    const { saved, failed } = await saveAll({
      thresholds: thresholds.value,
      drafts: drafts.value,
      season: season.value,
      equipmentIds: scope.value === 'ovens' ? ovenIds.value : [props.equipmentId],
      reason: reason.value.trim(),
      by: auth.user?.name,
    })
    // 하나도 저장 못 했으면 확인 창을 열어 둔 채 사유만 알림
    if (!saved.length) {
      toast(failed[0]?.message ?? '저장하지 못했습니다.', { type: 'err' })
      return
    }
    confirmOpen.value = false
    reason.value = ''
    if (failed.length) {
      // 건조로 3대 중 일부만 실패: 어느 설비가 저장되지 않았는지 알림
      const names = failed.map((f) => equipmentName(f.id)).join(', ')
      const why = failed[0].message.replace(/\.$/, '')
      toast(`${names}는 저장하지 못했습니다(${why}). 나머지 설비는 저장했습니다.`, { type: 'warn' })
    } else {
      const name = equipmentRow.value?.name
      const s = season.value
      toast(
        s === thresholds.value.activeSeason
          ? `${name} ${SEASON_META[s].label} 기준값을 저장했습니다. 지금부터 적용됩니다.`
          : `${name} ${SEASON_META[s].label} 기준값을 저장했습니다. ${validFrom.value.slice(5).replace('-', '월 ')}일부터 적용됩니다.`,
      )
    }
    await loadTree()
    await loadEquipment({ keepSeason: true })
  } finally {
    saving.value = false
  }
}

init()
</script>

<template>
  <Teleport to="body">
    <!-- 닫을 때 사라지는 움직임이 끝난 뒤 close 이벤트 (화면설계서 R2 창 열림·닫힘) -->
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }" appear @after-leave="emit('close')">
      <BaseModal v-if="shown" :width="modalWidth" label="설비 기준값 관리" @close="requestClose">
        <div class="m-head">
          <AppIcon name="slider" :size="20" class="hico" />
          <h2>설비 기준값 관리</h2>
          <span class="loc">{{ path }}{{ equipmentRow ? ` · ${equipmentRow.name}` : '' }}</span>
          <button type="button" class="m-close" aria-label="닫기" @click="requestClose"><AppIcon name="x" /></button>
        </div>

        <p v-if="loading" class="state">불러오는 중</p>
        <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>

        <div v-else class="th-body">
          <div class="middle">
            <ThresholdHeader
              v-if="equipmentRow"
              v-model:scope="scope"
              :equipment="equipmentRow"
              :tags="sensors.map((s) => s.tag)"
              :path="path"
              :change-count="changes.length"
              :can-save="canSave"
              @undo="undo"
              @save="openConfirm"
            />

            <!-- 설비 설정: 사용 상태 · 배기팬 자동 제어 (바로 저장) -->
            <EquipmentSettingsCard
              :active="thresholds.active !== false"
              :fan-auto="thresholds.fanAuto ?? null"
              :busy="settingBusy"
              @toggle="onToggle"
            />

            <SeasonTabs :season="season" :active-season="thresholds.activeSeason" @select="selectSeason" />

            <!-- 센서별 카드 3칸: 이 설비에 없는 종류는 점선 칸 -->
            <div v-if="sensors.length" class="cards">
              <template v-for="(t, i) in SENSOR_TYPES" :key="`${season}-${t.type}`">
                <SensorThresholdCard
                  v-if="sensorOf(t.type)"
                  class="rise"
                  :style="{ '--i': i + 2 }"
                  :sensor="sensorOf(t.type)"
                  :season="season"
                  :draft="drafts[sensorOf(t.type).sensorId]"
                  @input="(f, v) => onInput(sensorOf(t.type), f, v)"
                  @focus="(f) => (focus = { sensorId: sensorOf(t.type).sensorId, field: f })"
                />
                <div v-else class="slot rise" :style="{ '--i': i + 2 }">
                  <b>{{ t.label }} 센서 없음</b>
                  이 설비에는 {{ t.label }} 센서가 연결되어 있지 않습니다.
                </div>
              </template>
            </div>
            <div v-else class="no-sensor">
              <b>연결된 센서가 없습니다</b>
              {{ equipmentRow?.name }} 설비에는 기준값을 정할 센서가 없습니다.
            </div>
          </div>

          <!-- 변경 이력: 왼쪽(머리~센서 카드)과 같은 높이, 길면 목록만 스크롤 -->
          <div class="history-box">
            <HistoryCard
              class="history"
              :equipment-name="equipmentRow?.name ?? ''"
              :sensors="sensors"
              :rows="history"
              :pending="changes.filter((c) => c.after !== null)"
              :sensor="focusSensor"
              :saved-warn="focusSensor ? savedOf(focusSensor, season).warn : null"
              :next-warn="focusSensor ? nextOf(focusSensor).warn : null"
              :range="historyRange"
              :user-label="userLabel"
              :busy="historyBusy"
              @query="onHistoryQuery"
            />
          </div>

          <!-- 변경 시뮬레이션: 아래 줄 전체 너비 -->
          <SimulationCard
            v-if="focusSensor"
            class="sim"
            :sensor="focusSensor"
            :field="focus.field"
            :saved="savedOf(focusSensor, season)"
            :next="nextOf(focusSensor)"
            :series="weekCache[focusSensor.sensorId] ?? null"
            :sim="sim"
            :busy="simBusy"
          />
        </div>
      </BaseModal>
    </Transition>

    <!-- 저장 확인 -->
    <ConfirmModal :open="confirmOpen" title="기준값을 저장할까요?" ok-text="저장" icon="save" :busy="saving" :ok-disabled="!reason.trim()" @ok="save" @cancel="confirmOpen = false">
      <SaveSummary
        v-model:reason="reason"
        :changes="changes"
        :scope="scope"
        :season="season"
        :active-season="thresholds?.activeSeason ?? 'WINTER'"
        :valid-from="validFrom"
      />
    </ConfirmModal>

    <!-- 바꾼 값이 있는데 계절을 바꾸거나 창을 닫을 때 -->
    <ConfirmModal :open="!!pending" title="바꾼 값을 저장하지 않았습니다" ok-text="저장하지 않고 진행" @ok="confirmDiscard" @cancel="pending = null">
      저장하지 않고 {{ pending?.what }} 바꾼 값이 사라집니다.
    </ConfirmModal>
  </Teleport>
</template>

<style scoped>
/*
 * 창 안 배치 (2026-11-19 재원님 요청으로 바꿈)
 *   윗줄: 가운데(머리·설비 설정·계절·센서 카드) 1100 | 변경 이력 418 — 변경 이력은 센서 카드 줄 끝에 높이를 맞춤
 *   아랫줄: 변경 시뮬레이션이 창 너비 전체
 * 1400px 이하 화면에서는 한 줄에 하나씩
 */
.hico {
  color: var(--color-primary);
}

.th-body {
  display: grid;
  grid-template-columns: minmax(0, 1100fr) minmax(0, 418fr);
  gap: 16px;
  padding: 20px 24px 24px;
  background: var(--color-bg, #f6f7f9);
}

/* 변경 이력 칸: 높이는 왼쪽 칸이 정하고(내용이 길어도 줄을 늘리지 않게 absolute), 카드는 그 칸을 가득 채움 */
.history-box {
  position: relative;
  min-width: 0;
}

.history-box .history {
  position: absolute;
  inset: 0;
}

.sim {
  grid-column: 1 / -1;
}

@media (max-width: 1400px) {
  .th-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .history-box .history {
    position: static;
    max-height: 640px; /* 한 줄 배치에서는 이력이 길어도 이 높이까지만 (목록이 스크롤) */
  }
}

.middle {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

/* 센서가 없는 칸 (설계안 .slot) */
.slot,
.no-sensor {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 376px;
  padding: 20px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  background: #fafbfc;
  font-size: 13px;
  color: var(--color-text-weak);
  text-align: center;
}

.slot b,
.no-sensor b {
  font-size: 14px;
  color: var(--color-text-sub);
}

.state {
  padding: 60px 28px;
  text-align: center;
  color: var(--color-text-sub);
}

.state.error {
  color: var(--color-level-alarm-text);
}
</style>
