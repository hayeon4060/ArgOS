<!--
  점검 관리 화면 — 수기 서류 등록 (화면설계안 v-inspect.js vInspect(), 화면설계서 E1, API 명세서 E1)
  - 이 화면은 데이터를 불러오고 부품끼리 이어 주는 일만 함. 그리는 부분은 features/inspection/
  - 배치 (설계안 1920): 왼쪽 400 (이번 달 점검 현황 · 서류별 등록 현황 · 점검 일정) | 오른쪽 1464 (수기 서류 등록)
    오른쪽 카드 안: 스캔 원본 628 | 인식 결과 464 | 올리기·등록 목록 300
  - 흐름: 파일 올리기 → 문자 인식(2초마다 확인) → 원본 대조(확인·고치기) → 저장 (useOcrDoc)
  - 본사 관리자는 열람만 (화면설계서 E1 '본사 관리자: 올리기·고치기 불가')
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { USE_MOCK } from '@/api/mock'
import { getDocForms, getInspectionSummary, getInspections } from '@/api/inspections'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import { toast } from '@/utils/toast'
import FormStatusCard from '@/features/inspection/FormStatusCard.vue'
import InspectionListModal from '@/features/inspection/InspectionListModal.vue'
import MonthSummaryCard from '@/features/inspection/MonthSummaryCard.vue'
import OcrFields from '@/features/inspection/OcrFields.vue'
import ScanViewer from '@/features/inspection/ScanViewer.vue'
import ScheduleCard from '@/features/inspection/ScheduleCard.vue'
import StepBar from '@/features/inspection/StepBar.vue'
import UploadQueue from '@/features/inspection/UploadQueue.vue'
import { FORM_LABEL, SAMPLE_FORM } from '@/features/inspection/inspectionMeta'
import { useOcrDoc } from '@/features/inspection/useOcrDoc'

const auth = useAuthStore()
const readOnly = computed(() => auth.role === ROLE.HQ_ADMIN)
const myName = computed(() => auth.user?.name ?? '')

/* ───────── 왼쪽: 이번 달 현황 ───────── */
const summary = ref(null)
async function loadSummary() {
  try {
    summary.value = await getInspectionSummary({ factoryId: auth.user?.scope?.factoryIds?.[0] })
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/* ───────── 오른쪽: 등록 목록 (최근 7건) ───────── */
const rows = ref([])
async function loadRows() {
  try {
    rows.value = (await getInspections({ page: 0, size: 7 })).items
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/* ───────── 서류 양식 (인식 결과 칸 순서·구역) ───────── */
const forms = ref([])
const formFields = computed(() => forms.value.find((f) => f.formType === ocr.doc.formType)?.fields ?? [])

/* ───────── 지금 서류 ───────── */
const ocr = useOcrDoc()
const doc = ocr.doc
const hlKey = ref('')
const reveal = ref(false)
const fieldsRef = ref(null)
const queueRef = ref(null)

/** 원본 보기 · 결과 칸 모양 */
const viewMode = computed(() => {
  if (!doc.id && doc.status !== 'UPLOADING' && doc.status !== 'FAILED') return 'empty'
  return { UPLOADING: 'up', RECOGNIZING: 'scan', FAILED: 'fail' }[doc.status] ?? 'review'
})
const fieldMode = computed(() => {
  if (viewMode.value === 'empty') return 'empty'
  return { UPLOADING: 'wait', RECOGNIZING: 'wait', FAILED: 'fail', DONE: 'saved' }[doc.status] ?? 'review'
})

/** 등록 목록: 올리는 중인 파일은 서버 목록에 아직 없어 맨 위에 임시 줄 */
const queueRows = computed(() =>
  doc.status === 'UPLOADING' ? [{ id: null, fileName: doc.fileName, formType: '', status: 'UPLOADING', progress: doc.upload }, ...rows.value] : rows.value,
)

/** 서류 열기 (설계안: 예시 원본은 F01 만 — 다른 서류·인식 중인 서류는 안내만) */
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
  if (readOnly.value) return
  try {
    await ocr.upload(file, { by: myName.value })
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
    await ocr.save(status, myName.value)
    if (status === 'REVIEW') toast('임시 저장했습니다. 등록 목록에서 이어서 검수할 수 있습니다.')
    else toast(`${FORM_LABEL[doc.formType]}를 저장했습니다.`)
    loadRows()
    if (status === 'DONE') loadSummary()
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
const listForm = ref('')
function openList(formType = '') {
  listForm.value = formType
  listOpen.value = true
}
function pickFromList(row) {
  listOpen.value = false
  openRow(row)
}

/* ───────── 처음 · 정리 ───────── */
// 목록에 인식 중인 서류가 있으면 2초마다 진행률 갱신 (명세서: 2초마다)
let tick = 0
function startTick() {
  clearInterval(tick)
  tick = setInterval(() => {
    if (rows.value.some((r) => r.status === 'RECOGNIZING')) {
      const before = rows.value.filter((r) => r.status === 'REVIEW').length
      loadRows().then(() => {
        // 다른 서류 인식이 끝나 검수 대기가 늘면 현황도 갱신
        if (rows.value.filter((r) => r.status === 'REVIEW').length !== before) loadSummary()
      })
    }
  }, 2000)
}

onMounted(async () => {
  try {
    forms.value = await getDocForms()
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
  await Promise.all([loadSummary(), loadRows()])
  // 설계안: 처음에는 검수 중인 밀폐공간 작업허가서를 열어 둠
  const first = rows.value.find((r) => r.formType === SAMPLE_FORM && r.status === 'REVIEW') ?? rows.value.find((r) => r.formType === SAMPLE_FORM)
  if (first) ocr.open(first)
  startTick()
})

onBeforeUnmount(() => {
  clearInterval(tick)
  ocr.dispose()
})
</script>

<template>
  <div class="inspection-page">
    <!-- 왼쪽 -->
    <div class="left">
      <MonthSummaryCard :summary="summary" />
      <FormStatusCard :forms="summary?.byForm ?? []" @open="openList" />
      <ScheduleCard :month="summary?.month ?? ''" :calendar="summary?.calendar ?? []" :factory-id="auth.user?.scope?.factoryIds?.[0] ?? null" />
    </div>

    <!-- 오른쪽: 수기 서류 등록 -->
    <section class="ocr base-card rise">
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
      </header>

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
          @all="openList()"
        />
      </div>
    </section>

    <InspectionListModal :open="listOpen" :form-type="listForm" @close="listOpen = false" @pick="pickFromList" />
  </div>
</template>

<style scoped>
/*
 * 배치 (설계안 1920 기준): 왼쪽 400 | 오른쪽 1464 — 같은 비율로 줄어듦
 * 오른쪽 카드 안: 원본 628 | 결과 464 | 목록 300
 */
.inspection-page {
  display: grid;
  grid-template-columns: minmax(0, 400fr) minmax(0, 1464fr);
  gap: 16px;
  align-items: start;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ocr {
  display: flex;
  flex-direction: column;
  height: max(760px, calc(var(--screen-h) - var(--topbar-height) - 40px));
  background: var(--color-surface);
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.ch {
  height: 48px;
  flex: none;
  display: flex;
  align-items: center;
  padding: 0 20px;
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

.spin-sm {
  width: 13px;
  height: 13px;
  margin-right: 4px;
  border: 2px solid #bfdbfe;
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite; /* base.css */
}

.body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 628fr) minmax(0, 464fr) minmax(0, 304fr);
  gap: 16px;
  padding: 16px;
}

/* 1700px 이하: 왼쪽 카드 3개를 위로 한 줄에 놓고, 수기 서류 등록 카드를 넓게 */
@media (max-width: 1700px) {
  .inspection-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .left {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-items: start;
  }
}
</style>
