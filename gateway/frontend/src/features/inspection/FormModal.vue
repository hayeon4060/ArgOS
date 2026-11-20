<!--
  서류 양식 창 (항상 서류 양식 모양으로 보고 씀 — 팀 양식 F02 환기·배기설비 점검일지 그대로)
  - 완료 건: 저장된 값이 채워진 양식 보기 (수기는 '스캔 원본 보기' 버튼도)
  - 예정 · 누락 건: 그 건의 양식 작성 — 날짜 · 시각 · 설비 · 점검자를 미리 채움
  - item 없이 열면('점검일지 작성'): 새 양식 — 위에서 설비를 고름 (양식은 F02 하나라 고르는 칸 없음)
  - 화면 양식이 없는 서류(수기 서류 등록으로 이은 F01 등)는 양식 대신 안내 + '스캔 원본 보기'
  - 작성: 노란 칸을 눌러 적고, 네모를 눌러 V. 저장하면 그 건이 완료(전자)로 바뀌고 이력에 반영 (saved)
  - 결과(양호 · 불량)는 양식에서 계산: 점검 항목에 불량이 하나라도 있으면 불량 (formDefs.resultOf)
  - 본사 관리자는 열람만 (작성 칸 없이 보기)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { createInspectionRecord, getScheduleForm, getScheduleOptions, saveScheduleForm } from '@/api/inspections'
import { serverNow } from '@/api/clock'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import InspBadge from './InspBadge.vue'
import PaperForm from './forms/PaperForm.vue'
import { blankValues, missingOf, resultOf } from './forms/formDefs'
import { FORM_LABEL, PAPER_FORM, RESULT, SOURCE, ymd } from './inspectionMeta'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 점검 건 (null 이면 새로 작성) */
  item: { type: Object, default: null },
  readOnly: { type: Boolean, default: false },
  /** 로그인한 사람 이름 (새 양식의 점검자) */
  myName: { type: String, default: '' },
  factoryId: { type: Number, default: null },
})

const emit = defineEmits(['close', 'saved', 'scan'])

const values = ref({})
const formType = ref(PAPER_FORM)
const source = ref(null)
const loading = ref(false)
const saving = ref(false)
/** 새로 작성할 때 고르는 설비 */
const equipments = ref([])
const equipmentId = ref(null)

/** 완료 건이면 보기, 아니면 작성 */
const viewing = computed(() => props.item?.status === 'DONE')
const editable = computed(() => !viewing.value && !props.readOnly)
const isNew = computed(() => !props.item)
const pickable = computed(() => equipments.value.filter((e) => e.formType === formType.value))
const equipment = computed(() => (isNew.value ? equipments.value.find((e) => e.id === equipmentId.value) : null))
const eqName = computed(() => props.item?.equipmentName ?? equipment.value?.name ?? '')
/** 화면에 그릴 수 있는 양식인지 (F02 만) */
const hasPaper = computed(() => formType.value === PAPER_FORM)

watch(
  () => props.open,
  (open) => open && start(),
)

async function start() {
  source.value = null
  values.value = {}
  if (isNew.value) return startNew()
  formType.value = props.item.formType
  if (!viewing.value) {
    const it = props.item
    values.value = blankValues({ date: it.date, time: it.time, equipmentNo: it.equipmentNo, inspector: it.assignee })
    return
  }
  loading.value = true
  try {
    const r = await getScheduleForm(props.item.scheduleId)
    values.value = r.values ?? {}
    source.value = r.source
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    loading.value = false
  }
}

/** 새 양식: 설비 목록을 받고 지금 시각 · 내 이름으로 채움 */
async function startNew() {
  formType.value = PAPER_FORM
  try {
    equipments.value = (await getScheduleOptions(props.factoryId ?? undefined)).equipments
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
  pickFirst()
}

function pickFirst() {
  equipmentId.value = pickable.value[0]?.id ?? null
  fillNew()
}

function fillNew() {
  const now = serverNow()
  const hm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  const e = equipment.value
  values.value = blankValues({ date: ymd(now), time: hm, equipmentNo: e?.code, inspector: props.myName })
}

async function save() {
  const v = values.value
  const missing = missingOf(formType.value, v)
  if (missing) {
    toast(missing, { type: 'warn' })
    return
  }
  saving.value = true
  try {
    const result = resultOf(formType.value, v)
    const saved = isNew.value
      ? await createInspectionRecord({ formType: formType.value, equipmentId: equipmentId.value, date: v.inspectedAt.slice(0, 10), time: v.inspectedAt.slice(11, 16), values: v, result })
      : await saveScheduleForm(props.item.scheduleId, { values: v, result })
    toast(`${FORM_LABEL[formType.value]}를 저장했습니다. 점검 이력에 '완료 · 전자'로 반영됩니다.`)
    emit('saved', saved)
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="866" :label="FORM_LABEL[formType]" @close="emit('close')">
        <div class="wrap">
          <div class="m-head">
            <h2>{{ FORM_LABEL[formType] }}</h2>
            <template v-if="viewing">
              <InspBadge v-if="source" :tone="SOURCE[source].cls">{{ source === 'PAPER' ? '수기 · 스캔본에서 인식' : '전자 작성' }}</InspBadge>
              <InspBadge v-if="item.result" :tone="RESULT[item.result].cls">{{ RESULT[item.result].label }}</InspBadge>
            </template>
            <InspBadge v-else-if="editable" tone="plan">새로 작성</InspBadge>
            <!-- 새 양식: 양식 · 설비 고르기 -->
            <template v-if="isNew">
              <select v-model="equipmentId" class="eq" aria-label="설비" @change="fillNew">
                <option v-for="e in pickable" :key="e.id" :value="e.id">{{ e.name }} ({{ e.code }})</option>
              </select>
            </template>
            <span v-else class="loc">{{ item.date }} {{ item.time }} · {{ item.equipmentName }}</span>
            <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
          </div>

          <div class="paperwrap">
            <div v-if="loading" class="empty">양식을 불러오는 중…</div>
            <PaperForm v-else-if="hasPaper" :values="values" :edit="editable" :equipment-name="eqName" />
            <div v-else class="empty">
              {{ formType }} {{ FORM_LABEL[formType] }} 는 화면 양식이 없습니다.<br />아래 '스캔 원본 보기'로 원본을 확인하세요.
            </div>
          </div>

          <div class="mf">
            <template v-if="editable">
              <span class="hint">노란 칸을 눌러 적고, 네모를 눌러 V 표시</span>
              <span class="gap" />
              <button type="button" class="btn" @click="emit('close')">취소</button>
              <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
                <span v-if="saving" class="m-spin" /><AppIcon v-else name="ok" :size="15" />저장
              </button>
            </template>
            <template v-else>
              <button v-if="viewing && item.source === 'PAPER'" type="button" class="btn" @click="emit('scan', item)">
                <AppIcon name="scan" :size="15" />스캔 원본 보기
              </button>
              <span v-if="!viewing" class="hint">본사 관리자는 열람만 할 수 있습니다.</span>
              <span class="gap" />
              <button type="button" class="btn btn-primary" @click="emit('close')">닫기</button>
            </template>
          </div>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 머리 · 아래 버튼은 고정, 양식만 스크롤 */
.wrap {
  display: flex;
  flex-direction: column;
  max-height: calc(var(--screen-h) - 48px);
}

.m-head {
  flex: none;
}

.eq {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13px;
}

.paperwrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 0;
  background: #e5e7eb;
}

.empty {
  padding: 80px 0;
  text-align: center;
  color: var(--color-text-weak);
}

.mf {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.hint {
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.gap {
  flex: 1;
}
</style>
