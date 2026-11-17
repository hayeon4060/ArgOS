<!--
  체크리스트 진행 현황 창 (화면설계안 js/v-modals.js mChecklist(), 화면설계서 C3)
  - 머리: 진행 중 / 모두 완료 / 응답 없음(10분 넘게 진행 없음)
  - 왼쪽: 진행 고리(2/5 완료), 대상 설비 · 담당자 · 전송 시각 · 경과 시간(1초마다) · 현재 값
  - 오른쪽: 항목 진행과 현장 메모(ChecklistItemsView), 다시 알림(30초에 한 번) · 담당자 변경, 조치 확인
  - 조치 확인: 담당자가 앱에서 모두 마치고 완료 보고한 뒤에만 켜짐 → 결과·한 줄 메모 → POST /checklists/{id}/confirm → 주의 해제
  - 본사 관리자는 열람만 (단추가 꺼짐)
  - 데이터: GET /checklists/{id}, 현재 값은 GET /equipment/{id}, 담당자 목록은 GET /processes/{id}/workers
    체크리스트가 진행되면(SSE checklist.updated, 지금은 예시 상황) 다시 불러옴
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ChecklistItemsView from './ChecklistItemsView.vue'
import ReassignPopover from './ReassignPopover.vue'
import { getChecklist, getProcessWorkers, confirmChecklist, reassignChecklist, remindChecklist } from '@/api/checklists'
import { getEquipment } from '@/api/equipment'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import { useAlertStore } from '@/stores/alert'
import { LEVEL, sensorTypeOf } from '@/types/codes'
import { useLiveReload } from '@/utils/useLiveReload'
import { clearToasts, toast } from '@/utils/toast'

const props = defineProps({
  checklistId: { type: Number, required: true },
})

const alert = useAlertStore()

const checklist = ref(null)
const sensor = ref(null) // 대상 센서 지금 값 { tag, value, unit, level, type }
const workers = ref([])
const errorMessage = ref('')
let serverOffset = 0 // 서버 시계 - 내 시계 (ms): 경과 시간을 서버 기준으로 세려고

async function load() {
  try {
    const c = await getChecklist(props.checklistId)
    checklist.value = c
    if (c.serverTime) serverOffset = new Date(c.serverTime).getTime() - Date.now()
    const [eq, list] = await Promise.all([getEquipment(c.equipmentId), workers.value.length ? workers.value : getProcessWorkers(c.processId)])
    sensor.value = eq.sensors.find((s) => s.tag === c.sensorTag) ?? null
    workers.value = list
  } catch (error) {
    errorMessage.value = error.message
  }
}

onMounted(load)
useLiveReload(load) // 앱에서 항목을 체크하면 (SSE checklist.updated) 다시

const total = computed(() => checklist.value?.items.length ?? 0)
const doneCount = computed(() => checklist.value?.items.filter((i) => i.doneAt).length ?? 0)
const reported = computed(() => checklist.value?.status === 'REPORTED')

/* ───────── 1초마다: 경과 시간, 다시 알림 남은 초 ───────── */
const now = ref(Date.now())
const timer = setInterval(() => (now.value = Date.now()), 1000)
onBeforeUnmount(() => clearInterval(timer))

const pad = (n) => String(n).padStart(2, '0')
const serverNow = () => now.value + serverOffset
const elapsed = computed(() => {
  if (!checklist.value) return ''
  const sec = Math.max(0, Math.floor((serverNow() - new Date(checklist.value.sentAt).getTime()) / 1000))
  return `${pad(Math.floor(sec / 3600))}:${pad(Math.floor(sec / 60) % 60)}:${pad(sec % 60)}`
})

/** 10분 넘게 진행이 없으면 '응답 없음' (화면설계서 C3) */
const noResponse = computed(() => {
  const c = checklist.value
  if (!c || reported.value) return false
  const last = [...c.items].reverse().find((i) => i.doneAt)?.doneAt ?? c.sentAt
  return serverNow() - new Date(last).getTime() > 10 * 60 * 1000
})

const headChip = computed(() =>
  reported.value ? { cls: 'ok', text: '모두 완료' } : noResponse.value ? { cls: 'warn', text: '응답 없음' } : { cls: 'info', text: '진행 중' },
)

const sensorText = computed(() => {
  const s = sensor.value
  if (!s) return ''
  return `${s.tag} ${Number(s.value).toFixed(sensorTypeOf(s.type)?.dec ?? 0)} ${s.unit}`
})

/* ───────── 다시 알림 (30초에 한 번) ───────── */
const remindLeft = computed(() => {
  const at = checklist.value?.remindAvailableAt
  return at ? Math.max(0, Math.ceil((new Date(at).getTime() - now.value) / 1000)) : 0
})

async function onRemind() {
  try {
    const r = await remindChecklist(props.checklistId)
    checklist.value = { ...checklist.value, remindCount: r.remindCount, remindAvailableAt: r.remindAvailableAt }
    toast(`${checklist.value.assignee.name} 님에게 다시 알렸습니다 (${r.remindCount}번째).`)
  } catch (error) {
    toast(error.message, { type: 'warn' })
  }
}

/* ───────── 담당자 변경 ───────── */
const reassignOpen = ref(false)
const busy = ref(false)
const nameHighlight = ref(false)

async function onReassign({ assigneeId, reason }) {
  busy.value = true
  try {
    await reassignChecklist(props.checklistId, { assigneeId, reason })
    reassignOpen.value = false
    await load()
    // 바뀐 담당자 이름을 잠깐 강조 (300ms)
    nameHighlight.value = true
    setTimeout(() => (nameHighlight.value = false), 300)
    toast(`담당자를 ${checklist.value.assignee.name} 님으로 바꿨습니다.`)
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}

/* ───────── 조치 확인 ───────── */
const RESULTS = [
  { value: 'RESOLVED', label: '정상 복귀' },
  { value: 'PARTS_PLANNED', label: '부품 교체 예정' },
  { value: 'NEED_RECHECK', label: '추가 점검 필요' },
]
const result = ref('RESOLVED')
const memo = ref('')

async function onConfirm() {
  busy.value = true
  try {
    await confirmChecklist(props.checklistId, { result: result.value, memo: memo.value })
    alert.close()
    clearToasts()
    toast('조치를 확인했습니다. 주의가 해제되었습니다.')
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <BaseModal :width="760" label="체크리스트 진행 현황" @close="alert.close()">
    <template #default="{ close }">
      <div class="m-head">
        <h2>체크리스트 진행 현황</h2>
        <span class="m-chip big" :class="headChip.cls">{{ headChip.text }}</span>
        <button type="button" class="m-close" aria-label="닫기" @click="close"><AppIcon name="x" /></button>
      </div>

      <p v-if="errorMessage" class="state">{{ errorMessage }}</p>
      <p v-else-if="!checklist" class="state">불러오는 중</p>

      <div v-else class="cols">
        <!-- 왼쪽: 진행 고리 + 대상 정보 -->
        <section class="left">
          <ProgressRing
            :value="doneCount"
            :total="total"
            :size="170"
            :stroke="14"
            :color="reported ? 'var(--color-level-normal)' : 'var(--color-primary)'"
            class="ring"
          >
            <b class="mono count">{{ doneCount }}<small>/{{ total }}</small></b>
            <span class="done">완료</span>
          </ProgressRing>

          <dl class="info">
            <div><dt>대상 설비</dt><dd>{{ checklist.equipmentName }}</dd></div>
            <div>
              <dt>담당자</dt>
              <dd :class="{ flash: nameHighlight }">{{ checklist.assignee.name }} (도장 공정)</dd>
            </div>
            <div><dt>전송 시각</dt><dd class="mono">{{ checklist.sentAt.slice(11, 19) }}</dd></div>
            <div><dt>경과 시간</dt><dd class="mono">{{ elapsed }}</dd></div>
            <div>
              <dt>현재 값</dt>
              <dd class="mono" :class="{ warn: sensor?.level === LEVEL.WARN, alarm: sensor?.level === LEVEL.ALARM }">{{ sensorText }}</dd>
            </div>
            <div v-if="checklist.remindCount"><dt>다시 알림</dt><dd>{{ checklist.remindCount }}번</dd></div>
          </dl>
        </section>

        <!-- 오른쪽: 항목 + 메모 + 단추 -->
        <section>
          <ChecklistItemsView :items="checklist.items" :report-memo="checklist.reportMemo ?? ''" />

          <div v-if="!reported" class="m-actions sub">
            <button type="button" class="btn" :disabled="!alert.canAct || remindLeft > 0" @click="onRemind">
              <AppIcon name="bell" :size="15" />다시 알림<span v-if="remindLeft" class="mono wait">{{ remindLeft }}초</span>
            </button>
            <div class="reassign">
              <button type="button" class="btn" :disabled="!alert.canAct" @click="reassignOpen = !reassignOpen">
                <AppIcon name="user" :size="15" />담당자 변경
              </button>
              <ReassignPopover
                v-if="reassignOpen"
                :workers="workers"
                :current-id="checklist.assignee.id"
                :busy="busy"
                @apply="onReassign"
                @cancel="reassignOpen = false"
              />
            </div>
          </div>

          <div v-if="reported && alert.canAct" class="result">
            <select v-model="result" aria-label="조치 결과">
              <option v-for="r in RESULTS" :key="r.value" :value="r.value">{{ r.label }}</option>
            </select>
            <input v-model="memo" type="text" maxlength="80" placeholder="한 줄 메모 (선택)" />
          </div>

          <div class="confirm">
            <span class="msg" :class="{ ok: reported }">
              <template v-if="!alert.canAct">본사 관리자는 진행 상황을 볼 수만 있습니다.</template>
              <template v-else-if="reported">{{ total }}개 항목이 모두 완료되었습니다. 조치를 확인해 주세요.</template>
              <template v-else>{{ total }}개 항목이 모두 완료되면 조치 확인을 할 수 있습니다.</template>
            </span>
            <button
              type="button"
              class="btn btn-primary"
              :class="{ ready: reported && alert.canAct }"
              :disabled="!reported || !alert.canAct || busy"
              @click="onConfirm"
            >
              조치 확인
            </button>
          </div>
        </section>
      </div>
    </template>
  </BaseModal>
</template>

<style scoped>
.state {
  padding: 60px 28px;
  text-align: center;
  color: var(--color-text-sub);
}

/* 설계안: 왼쪽 220 · 오른쪽 456 */
.cols {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 28px;
  padding: 24px 28px 28px;
}

.left {
  text-align: center;
}

.count {
  font-size: 40px;
  font-weight: 600;
}

.count small {
  font-size: 24px;
  color: var(--color-text-weak);
}

.done {
  font-size: 13px;
  color: #64748b;
}

.info {
  margin: 22px 0 0;
  font-size: 13px;
  line-height: 26px;
  color: #64748b;
  text-align: left;
}

.info div {
  display: flex;
}

.info dd {
  margin: 0 0 0 auto;
  font-weight: 700;
  color: var(--color-text);
  border-radius: var(--radius-xs);
  transition: background 300ms;
}

.info dd.mono {
  font-weight: 500;
}

.info dd.warn {
  font-weight: 600;
  color: var(--color-level-warn-text);
}

.info dd.alarm {
  font-weight: 600;
  color: var(--color-level-alarm-text);
}

/* 담당자가 바뀌면 이름 바탕이 잠깐 노랗게 */
.info dd.flash {
  background: #fef3c7;
}

.sub {
  margin-top: 12px;
}

.sub .btn {
  height: 38px;
  font-size: 13px;
}

.wait {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.reassign {
  position: relative;
}

.result {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.result select,
.result input {
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.result input {
  flex: 1;
}

.confirm {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.msg {
  font-size: 12.5px;
  color: #64748b;
}

.msg.ok {
  font-weight: 600;
  color: var(--color-level-normal-text);
}

.confirm .btn {
  margin-left: auto;
  height: 44px;
  padding: 0 22px;
}

/* 켜지는 순간 한 번 강조 (화면설계서 C3) */
.confirm .btn.ready {
  animation: ready-pop 600ms ease-out;
}

@keyframes ready-pop {
  0% {
    box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.5);
  }
  100% {
    box-shadow: 0 0 0 10px rgba(37, 99, 235, 0);
  }
}
</style>
