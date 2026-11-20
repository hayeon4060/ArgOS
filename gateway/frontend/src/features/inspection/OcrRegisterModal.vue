<!--
  수기 서류 등록 창 — 예전 점검 관리 화면 오른쪽 카드(4단계)를 그대로 창 안으로 옮김
  - 흐름: 파일 올리기 → 문자 인식(2초마다 확인) → 원본 대조(확인 · 고치기) → 저장 (useOcrDoc, 바뀐 것 없음)
  - 안쪽 부품도 그대로: StepBar · ScanViewer(원본 628) · OcrFields(인식 결과 464) · UploadQueue(올리기 · 등록 목록 300)
  - 열 때 file 이 있으면(서류 등록 카드에 끌어다 놓은 파일) 바로 올림
  - 창을 닫으면 2초 확인 타이머 · 원본 그림 주소를 정리 → 다시 열면 처음부터
  - 저장 · 올리기가 있었으면 닫을 때 changed → 화면이 점검 이력을 다시 불러옴
  - 추가(2026-11-20): 인식이 끝나면 '점검 건 연결' 줄(ScheduleLinkBar) — 인식 값으로 맞는 예정 · 누락 건을 추천,
    사용자가 바꿀 수 있음. 검수 완료 저장 때 같이 보내 그 건이 '완료 · 수기'로 바뀜 (없으면 새 이력)
  - 본사 관리자는 열람만 (화면설계서 E1)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { USE_MOCK } from '@/api/mock'
import { getDocForms, getInspections, getSchedule } from '@/api/inspections'
import { serverNow } from '@/api/clock'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import InspectionListModal from './InspectionListModal.vue'
import OcrFields from './OcrFields.vue'
import ScanViewer from './ScanViewer.vue'
import ScheduleLinkBar from './ScheduleLinkBar.vue'
import StepBar from './StepBar.vue'
import UploadQueue from './UploadQueue.vue'
import { FORM_LABEL, SAMPLE_FORM, addDays, ymd } from './inspectionMeta'
import { suggestSchedule } from './scheduleMatch'
import { useOcrDoc } from './useOcrDoc'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 서류 등록 카드에서 받은 파일 (없으면 검수 대기 서류를 열어 둠) */
  file: { type: Object, default: null },
  readOnly: { type: Boolean, default: false },
  myName: { type: String, default: '' },
})

const emit = defineEmits(['close', 'changed'])

/* ───────── 등록 목록 (최근 7건) ───────── */
const rows = ref([])
async function loadRows() {
  try {
    rows.value = (await getInspections({ page: 0, size: 7 })).items
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/* ───────── 서류 양식 (인식 결과 칸 순서 · 구역) ───────── */
const forms = ref([])
const formFields = computed(() => forms.value.find((f) => f.formType === ocr.doc.formType)?.fields ?? [])

/* ───────── 지금 서류 ───────── */
const ocr = useOcrDoc()
const doc = ocr.doc
const hlKey = ref('')
const reveal = ref(false)
const fieldsRef = ref(null)
const queueRef = ref(null)
let changed = false

/** 원본 보기 · 결과 칸 모양 */
const viewMode = computed(() => {
  if (!doc.id && doc.status !== 'UPLOADING' && doc.status !== 'FAILED') return 'empty'
  return { UPLOADING: 'up', RECOGNIZING: 'scan', FAILED: 'fail' }[doc.status] ?? 'review'
})
const fieldMode = computed(() => {
  if (viewMode.value === 'empty') return 'empty'
  return { UPLOADING: 'wait', RECOGNIZING: 'wait', FAILED: 'fail', DONE: 'saved' }[doc.status] ?? 'review'
})

/* ───────── 점검 건 연결 (스캔본 ↔ 점검 건) ───────── */
const candidates = ref([]) // 오늘 포함 최근 7일의 예정 · 누락 건
const linkId = ref(null) // 고른 건 (null = 연결 안 함)
const linked = ref('') // 저장 뒤 이은 결과 문구
const suggested = computed(() => suggestSchedule(candidates.value, { formType: doc.formType, fields: doc.fields, scheduleId: doc.scheduleId }))
/** 인식이 끝나 원본 대조 중일 때만 보임 (저장한 뒤에는 이은 결과 문구) */
const showLink = computed(() => fieldMode.value === 'review' || !!linked.value)

async function loadCandidates() {
  const today = ymd(serverNow())
  try {
    const list = await getSchedule({ from: addDays(today, -6), to: today })
    candidates.value = list.filter((it) => it.status !== 'DONE').sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time))
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

// 다른 서류를 열거나 인식이 끝나면(REVIEW) 추천 건으로 다시 고름 (저장해 DONE 이 될 때는 그대로 둠)
watch(
  () => [doc.id, doc.status],
  ([id, status], [oldId]) => {
    if (id === oldId && status !== 'REVIEW') return
    linkId.value = suggested.value
    linked.value = ''
  },
)

const nameOf = (id) => {
  const c = candidates.value.find((x) => x.scheduleId === id)
  return c ? `${c.date.slice(5)} ${c.time} ${c.equipmentName}` : ''
}

/** 등록 목록: 올리는 중인 파일은 서버 목록에 아직 없어 맨 위에 임시 줄 */
const queueRows = computed(() =>
  doc.status === 'UPLOADING' ? [{ id: null, fileName: doc.fileName, formType: '', status: 'UPLOADING', progress: doc.upload }, ...rows.value] : rows.value,
)

/** 서류 열기 (설계안: 예시 원본은 F01 만 — 다른 서류 · 인식 중인 서류는 안내만) */
function openRow(row) {
  if (row.id === doc.id) return
  if (row.status === 'RECOGNIZING') {
    toast('아직 글자를 읽는 중입니다. 인식이 끝나면 검수할 수 있습니다.', { type: 'info' })
    return
  }
  if (row.formType !== SAMPLE_FORM && row.status !== 'FAILED') {
    toast('예시 서류는 밀폐공간 작업허가서만 준비되어 있습니다.', { type: 'info' })
    return
  }
  hlKey.value = ''
  ocr.open(row)
}

/** 파일 올리기 */
async function upload(file) {
  if (props.readOnly) return
  try {
    await ocr.upload(file, { by: props.myName })
    changed = true
    loadRows()
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/** 예시 파일 올리기 (예시 모드에서 올리기 영역을 누를 때) */
function uploadSample() {
  const d = new Date()
  const mmdd = `${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  upload({ name: `밀폐공간 작업허가서_${mmdd}_${rows.value.length + 1}.pdf`, size: 1 })
}

// 인식이 끝나면: 결과가 차례로 나타남 + 안내 + 목록 갱신
watch(
  () => doc.status,
  (now, before) => {
    if (before === 'RECOGNIZING' && now === 'REVIEW') {
      reveal.value = true
      setTimeout(() => (reveal.value = false), 1500)
      const n = doc.fields.filter((f) => f.needCheck).length
      toast(n ? `문자 인식을 마쳤습니다. 주황색으로 표시된 ${n}개 항목을 원본과 비교해 주세요.` : '문자 인식을 마쳤습니다.', { type: 'info', ms: 4200 })
      loadRows()
    }
    if (before === 'RECOGNIZING' && now === 'FAILED') {
      toast('문자 인식을 마치지 못했습니다.', { type: 'err' })
      loadRows()
    }
  },
)

/** 저장 (임시 저장 REVIEW · 검수 완료 DONE) */
async function save(status) {
  try {
    const link = linkId.value
    await ocr.save(status, props.myName, link)
    changed = true
    if (status === 'REVIEW') toast('임시 저장했습니다. 등록 목록에서 이어서 검수할 수 있습니다.')
    else {
      linked.value = link ? `'${nameOf(link)}' 점검 건을 완료 · 수기로 바꿨습니다.` : '연결할 점검 건 없이 새 점검 이력(완료 · 수기)으로 저장했습니다.'
      toast(`${FORM_LABEL[doc.formType]}를 저장했습니다. ${linked.value}`)
      loadCandidates()
    }
    loadRows()
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

async function reocr() {
  try {
    await ocr.reocr()
    loadRows()
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/** '다른 서류 올리기': 예시 모드는 예시 파일, 아니면 파일 선택 창 */
function uploadNext() {
  if (USE_MOCK) uploadSample()
  else queueRef.value?.$el.querySelector('input[type=file]')?.click()
}

/* ───────── 등록 목록 창 ───────── */
const listOpen = ref(false)
function pickFromList(row) {
  listOpen.value = false
  openRow(row)
}

/* ───────── 열기 · 닫기 ───────── */
// 목록에 인식 중인 서류가 있으면 2초마다 진행률 갱신 (명세서: 2초마다)
let tick = 0
function startTick() {
  clearInterval(tick)
  tick = setInterval(() => {
    if (rows.value.some((r) => r.status === 'RECOGNIZING')) loadRows()
  }, 2000)
}

async function start() {
  changed = false
  try {
    if (!forms.value.length) forms.value = await getDocForms()
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
  await Promise.all([loadRows(), loadCandidates()])
  if (props.file) upload(props.file)
  else {
    // 파일 없이 열면: 검수 중인 서류를 열어 둠 (예전 화면과 같음)
    const first = rows.value.find((r) => r.formType === SAMPLE_FORM && r.status === 'REVIEW') ?? rows.value.find((r) => r.formType === SAMPLE_FORM)
    if (first) ocr.open(first)
  }
  startTick()
}

function stop() {
  clearInterval(tick)
  ocr.dispose()
  listOpen.value = false
}

watch(
  () => props.open,
  (open) => (open ? start() : stop()),
)

function close() {
  emit('close')
  if (changed) emit('changed')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="1500" label="수기 서류 등록" @close="close">
        <section class="ocr">
          <header class="ch">
            <h3>수기 서류 등록</h3>
            <StepBar :stage="doc.stage" />
            <span class="kind">
              서류 종류
              <span class="m-chip info big">
                <template v-if="doc.status === 'FAILED'">구분하지 못함</template>
                <template v-else-if="doc.stage < 2 && viewMode !== 'empty'"><span class="spin-sm" />구분하는 중</template>
                <template v-else-if="doc.formType">{{ doc.formType }} {{ FORM_LABEL[doc.formType] }}</template>
                <template v-else>–</template>
              </span>
            </span>
            <button type="button" class="m-close" aria-label="닫기" @click="close"><AppIcon name="x" /></button>
          </header>

          <ScheduleLinkBar v-if="showLink" v-model="linkId" :candidates="candidates" :suggested="suggested" :disabled="readOnly" :linked="linked" />

          <div class="body">
            <ScanViewer
              :file-name="doc.fileName"
              :image-url="doc.imageUrl"
              :mode="viewMode"
              :upload-pct="doc.upload"
              :progress="doc.progress"
              :fields="doc.fields"
              :hl-key="hlKey"
              :reveal="reveal"
              :fail-message="doc.failMessage"
              @pick="(key) => fieldsRef?.focusField(key)"
              @retry="reocr"
            />
            <OcrFields
              ref="fieldsRef"
              :mode="fieldMode"
              :fields="doc.fields"
              :form-fields="formFields"
              :progress="doc.progress"
              :upload-pct="doc.upload"
              :need-check-left="ocr.needCheckLeft.value"
              :edited-count="ocr.editedCount.value"
              :read-only="readOnly"
              read-only-note="본사 관리자는 열람만 할 수 있습니다."
              :saving="doc.saving"
              :saved-at="doc.reviewedAt ?? ''"
              :saved-by="doc.reviewedBy ?? ''"
              :reveal="reveal"
              @confirm="ocr.confirm"
              @edit="ocr.edit"
              @focus="(key) => (hlKey = key)"
              @blur="hlKey = ''"
              @save="save"
              @reocr="reocr"
              @new="uploadNext"
            />
            <UploadQueue
              ref="queueRef"
              :rows="queueRows"
              :current-id="doc.id"
              :read-only="readOnly"
              :sample-on-click="USE_MOCK"
              @file="upload"
              @sample="uploadSample"
              @open="openRow"
              @all="listOpen = true"
            />
          </div>
        </section>
      </BaseModal>
    </Transition>
  </Teleport>

  <InspectionListModal :open="listOpen" @close="listOpen = false" @pick="pickFromList" />
</template>

<style scoped>
/* 창 크기: 화면 높이에서 위아래 여백만 뺌 (배율 반영 --screen-h) */
.ocr {
  height: calc(var(--screen-h) - 96px);
  display: flex;
  flex-direction: column;
}

.ch {
  height: 56px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 12px 0 20px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.ch h3 {
  font-size: 16px;
  font-weight: 700;
}

.kind {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.kind + .m-close {
  margin-left: 4px;
}

.spin-sm {
  width: 13px;
  height: 13px;
  margin-right: 4px;
  border: 2px solid #bfdbfe;
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite; /* base.css */
}

/* 안쪽 3칸: 원본 628 | 결과 464 | 목록 304 (예전 화면과 같은 비율) */
.body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 628fr) minmax(0, 464fr) minmax(0, 304fr);
  gap: 16px;
  padding: 16px;
  background: var(--color-bg);
}
</style>
