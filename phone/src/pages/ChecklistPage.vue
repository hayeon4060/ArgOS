<!--
  체크리스트 한 건 (수행계획서 '앱 화면' ② 체크리스트 확인 · ③ 담당자에게 상황 전달, API 명세서 M2)
  - 위: 어떤 이상인지 (단계 · 설비 · 주의 걸린 값 → 지금 값 · 보낸 사람)
  - 가운데: 항목 체크 (ChecklistItem). 체크할 때마다 서버에 저장 → 관제 웹 진행 창에 바로 보임
    앞 항목부터 차례로, 체크하면 다음 항목이 펼쳐짐
  - 아래: 모든 항목을 체크하면 '완료 보고'(한 줄 메모 선택) → 관제 웹에 '완료 · 확인 필요'
  - 보고 뒤: 공장 담당자 확인 기다림 → 확인되면 '조치 확인 완료'
  - 담당자가 바뀌었으면(웹 '담당자 변경') 보기만
  - 데이터: GET /checklists/{id}, 지금 값은 GET /equipment/{id}. 바뀌면(SSE, 지금은 예시 상황) 조용히 다시 불러옴
-->
<script setup>
import { Haptics, NotificationType } from '@capacitor/haptics'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getChecklist } from '@/api/checklists'
import { getEquipment } from '@/api/equipment'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { useAuthStore } from '@/stores/auth'
import { LEVEL, LEVEL_META, sensorTypeOf } from '@/types/codes'
import { useLiveReload } from '@/utils/useLiveReload'
import { toast } from '@/utils/toast'
import { checkChecklistItem, reportChecklist } from '../api/myChecklists'
import { CHECKLIST_STATUS_LABEL } from '../codes'
import ChecklistItem from '../components/ChecklistItem.vue'
import { useMyChecklistsStore } from '../stores/myChecklists'

const route = useRoute()
const auth = useAuthStore()
const my = useMyChecklistsStore()

const checklist = ref(null)
const sensor = ref(null) // 대상 센서 지금 값 { tag, value, unit, level, type }
const errorMessage = ref('')
const openIndex = ref(-1)
const busy = ref(false)
const reportMemo = ref('')

const mine = computed(() => checklist.value?.assignee.id === auth.user?.id)
const editable = computed(() => mine.value && ['SENT', 'IN_PROGRESS'].includes(checklist.value?.status))
const total = computed(() => checklist.value?.items.length ?? 0)
const doneCount = computed(() => checklist.value?.items.filter((i) => i.doneAt).length ?? 0)
const left = computed(() => total.value - doneCount.value)

async function load() {
  try {
    const c = await getChecklist(route.params.checklistId)
    const first = !checklist.value
    checklist.value = c
    errorMessage.value = ''
    // 처음 열면 차례인 항목을 펼쳐 둠
    if (first && editable.value) openIndex.value = c.items.findIndex((i) => !i.doneAt)
    const eq = await getEquipment(c.equipmentId)
    sensor.value = eq.sensors.find((s) => s.tag === c.sensorTag) ?? null
  } catch (error) {
    errorMessage.value = error.message
  }
}

onMounted(load)
useLiveReload(load)
// 알림 띠를 눌러 다른 체크리스트로 바로 오면 (같은 화면이라 다시 만들어지지 않음)
watch(
  () => route.params.checklistId,
  (id) => {
    if (!id) return
    checklist.value = null
    openIndex.value = -1
    load()
  },
)

const fmt = (value, type) => Number(value).toFixed(sensorTypeOf(type)?.dec ?? 0)
const hms = (iso) => iso?.slice(11, 19) ?? ''

/* ───────── 항목 체크 ───────── */
async function onSave(index, { done, result, memo }) {
  const item = checklist.value.items[index]
  busy.value = true
  try {
    await checkChecklistItem(checklist.value.id, item.id, { done, result, memo, userId: auth.user.id })
    await load()
    my.refresh()
    // 체크했으면 다음 차례 항목을 펼침, 풀었으면 그 항목을 그대로
    openIndex.value = done ? checklist.value.items.findIndex((i) => !i.doneAt) : index
  } catch (error) {
    toast(error.message, { type: 'err' })
    load()
  } finally {
    busy.value = false
  }
}

/* ───────── 완료 보고 ───────── */
async function onReport() {
  busy.value = true
  try {
    await reportChecklist(checklist.value.id, { memo: reportMemo.value.trim(), userId: auth.user.id })
    await load()
    my.refresh()
    openIndex.value = -1
    Haptics.notification({ type: NotificationType.Success }).catch(() => {})
    toast(`${checklist.value.sentBy} 님에게 완료 보고를 보냈습니다.`)
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <p v-if="errorMessage" class="state">{{ errorMessage }}</p>
    <p v-else-if="!checklist" class="state">불러오는 중</p>

    <template v-else>
      <!-- 어떤 이상인지 -->
      <section class="cause">
        <div class="cause-head">
          <LevelBadge :level="checklist.level" />
          <span class="status">{{ CHECKLIST_STATUS_LABEL[checklist.status] ?? checklist.status }}</span>
        </div>
        <h2>{{ checklist.equipmentName }}</h2>
        <dl>
          <div>
            <dt>주의 걸린 값</dt>
            <dd class="mono">{{ checklist.sensorTag }} {{ checklist.valueAtAlarm }} {{ checklist.unit }}</dd>
          </div>
          <div v-if="sensor">
            <dt>지금 값</dt>
            <dd class="mono now" :class="`is-${LEVEL_META[sensor.level]?.key}`">
              {{ fmt(sensor.value, sensor.type) }} {{ sensor.unit }}
              <small>{{ LEVEL_META[sensor.level]?.label }}</small>
            </dd>
          </div>
          <div>
            <dt>보낸 사람</dt>
            <dd>{{ checklist.sentBy }} (공장 담당자) · <span class="mono">{{ hms(checklist.sentAt) }}</span></dd>
          </div>
          <div v-if="checklist.remindCount">
            <dt>다시 알림</dt>
            <dd class="remind">{{ checklist.remindCount }}번 받음</dd>
          </div>
        </dl>
      </section>

      <p v-if="!mine" class="notice">
        <AppIcon name="info" :size="18" />담당자가 {{ checklist.assignee.name }} 님으로 바뀌었습니다. 이 체크리스트는 볼 수만 있습니다.
      </p>

      <!-- 진행 -->
      <div class="progress">
        <span>점검 항목</span>
        <b class="mono">{{ doneCount }}/{{ total }}</b>
      </div>
      <div class="bar"><span :style="{ width: `${(doneCount / (total || 1)) * 100}%` }" :class="{ full: !left }" /></div>

      <ChecklistItem
        v-for="(item, i) in checklist.items"
        :key="item.id"
        :item="item"
        :open="openIndex === i"
        :editable="editable"
        :next="i === doneCount"
        :last="i === doneCount - 1"
        :busy="busy"
        @toggle="openIndex = openIndex === i ? -1 : i"
        @save="onSave(i, $event)"
      />

      <!-- 완료 보고 -->
      <section v-if="editable" class="report">
        <label for="report-memo">공장 담당자에게 남길 말 <small>(선택)</small></label>
        <input
          id="report-memo"
          v-model="reportMemo"
          type="text"
          maxlength="80"
          placeholder="예: 벨트 장력 조정 후 전류 18 A 대 확인"
          :disabled="left > 0"
        />
        <button type="button" class="btn btn-primary send" :disabled="left > 0 || busy" @click="onReport">
          <AppIcon name="send" :size="17" />완료 보고
        </button>
        <p class="hint">
          <template v-if="left">{{ left }}개 항목이 남았습니다. 모두 체크하면 보고할 수 있습니다.</template>
          <template v-else>보고하면 관제실 화면에 '완료 · 확인 필요'가 뜹니다.</template>
        </p>
      </section>

      <section v-else-if="checklist.status === 'REPORTED'" class="result wait">
        <AppIcon name="clock" :size="22" />
        <div>
          <b>완료 보고함 · <span class="mono">{{ hms(checklist.reportedAt) }}</span></b>
          <p>{{ checklist.sentBy }} 님이 확인하면 알림이 해제됩니다.</p>
          <p v-if="checklist.reportMemo" class="said">“{{ checklist.reportMemo }}”</p>
        </div>
      </section>

      <section v-else-if="checklist.status === 'CONFIRMED'" class="result ok">
        <AppIcon name="check" :size="22" />
        <div>
          <b>조치 확인 완료 · <span class="mono">{{ hms(checklist.confirmedAt) }}</span></b>
          <p>조치가 확인되어 알림이 해제되었습니다. 수고하셨습니다.</p>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.cause {
  padding: 16px;
  border: 1px solid #fdba74;
  border-radius: 12px;
  background: var(--color-surface);
}

.cause-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-sub);
}

h2 {
  margin-top: 8px;
  font-size: 19px;
}

dl {
  margin: 10px 0 0;
}

dl div {
  display: flex;
  gap: 10px;
  padding: 5px 0;
  font-size: 14px;
}

dt {
  width: 84px;
  flex: none;
  color: var(--color-text-sub);
}

dd {
  margin: 0;
}

.now small {
  margin-left: 4px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-level-normal-text);
}

.now.is-warn,
.now.is-warn small {
  color: var(--color-level-warn-text);
}

.now.is-alarm,
.now.is-alarm small {
  color: var(--color-level-alarm-text);
}

.remind {
  color: var(--color-level-warn-text);
  font-weight: 600;
}

.notice {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-size: 13.5px;
  line-height: 20px;
}

.progress {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 20px 0 8px;
  font-size: 15px;
  font-weight: 700;
}

.bar {
  height: 8px;
  margin-bottom: 14px;
  border-radius: 4px;
  background: #e2e8f0;
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--color-primary);
  transition:
    width var(--m-value),
    background 300ms;
}

.bar span.full {
  background: var(--color-level-normal);
}

.report {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--color-border-card);
  border-radius: 12px;
  background: var(--color-surface);
}

.report label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.report label small {
  font-weight: 400;
  color: var(--color-text-weak);
}

.report input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  font-size: 14px;
}

.report input:disabled {
  background: var(--color-surface-sub);
}

.send {
  width: 100%;
  height: 52px;
  margin-top: 12px;
  border-radius: 10px;
  font-size: 16px;
}

.hint {
  margin-top: 8px;
  font-size: 12.5px;
  text-align: center;
  color: var(--color-text-sub);
}

.result {
  display: flex;
  gap: 12px;
  margin-top: 18px;
  padding: 16px;
  border-radius: 12px;
  font-size: 13.5px;
  line-height: 20px;
}

.result b {
  font-size: 15px;
}

.result.wait {
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
}

.result.ok {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

.said {
  margin-top: 4px;
  color: var(--color-text-sub);
}

.state {
  margin-top: 20px;
  text-align: center;
  color: var(--color-text-sub);
}
</style>
