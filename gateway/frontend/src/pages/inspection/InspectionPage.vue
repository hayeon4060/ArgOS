<!--
  점검 관리 화면 — 2026-11-20 개편안 A안 (재원님 확정)
  - 이 화면은 데이터를 불러오고 부품끼리 이어 주는 일만 함. 그리는 부분은 features/inspection/
  - 배치 (1920 기준)
      맨 위: 점검 서류 양식 바 (F02 빈 양식 내려받기 — 변전소 F03 은 뺌)          TemplateBar
      왼쪽 900: 위 점검 스케줄(달력)                                            ScheduleCard
                아래 서류 등록 (끌어다 놓기 → 수기 서류 등록 창)                    UploadCard → OcrRegisterModal
      오른쪽: 점검 이력 (점검일지 작성 · 월별 보고서)                             HistoryCard
  - 창: 스케줄 추가 · 서류 양식(보기 · 작성) · 스캔 원본 미리보기 · 월별 보고서 · 수기 서류 등록(예전 4단계 + 점검 건 연결)
  - 달력 · 이력 · 보고서는 모두 GET /inspections/schedule 목록 하나에서 옴 (useInspectionSchedule)
  - 본사 관리자는 열람만 (스케줄 추가 · 작성 · 서류 등록 불가 — 화면설계서 E1)
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import FormModal from '@/features/inspection/FormModal.vue'
import HistoryCard from '@/features/inspection/HistoryCard.vue'
import OcrRegisterModal from '@/features/inspection/OcrRegisterModal.vue'
import ReportModal from '@/features/inspection/ReportModal.vue'
import ScanPreviewModal from '@/features/inspection/ScanPreviewModal.vue'
import ScheduleAddModal from '@/features/inspection/ScheduleAddModal.vue'
import ScheduleCard from '@/features/inspection/ScheduleCard.vue'
import TemplateBar from '@/features/inspection/TemplateBar.vue'
import UploadCard from '@/features/inspection/UploadCard.vue'
import { downloadScan } from '@/features/inspection/scanFile'
import { useInspectionSchedule } from '@/features/inspection/useInspectionSchedule'

const auth = useAuthStore()
const readOnly = computed(() => auth.role === ROLE.HQ_ADMIN)
const myName = computed(() => auth.user?.name ?? '')
/** 공장 담당자는 자기 공장 (본사는 담당 범위 전체 → 없음) */
const factoryId = computed(() => auth.user?.scope?.factoryIds?.[0] ?? null)

/* ───────── 점검 건 (달력 · 이력) ───────── */
const sch = useInspectionSchedule(factoryId.value ?? undefined)
const { today, month, days, monthItems, historyItems, loading } = sch

watch(month, sch.loadMonth)
watch(days, sch.loadHistory)
onMounted(sch.reload)

/* ───────── 스케줄 추가 창 ───────── */
const addOpen = ref(false)
const addDate = ref('')
const focusDate = ref('')
function openAdd(date) {
  addDate.value = date
  addOpen.value = true
}
/** 추가한 뒤: 첫 날짜의 달로 가서 그날 목록을 열어 둠 */
function onAdded({ date }) {
  addOpen.value = false
  month.value = date.slice(0, 7)
  focusDate.value = ''
  sch.reload().then(() => (focusDate.value = date))
}

/* ───────── 서류 양식 창 (완료: 보기 / 예정 · 누락: 작성 / 없음: 새로 작성) ───────── */
const formOpen = ref(false)
const formItem = ref(null)
function openForm(item) {
  formItem.value = item
  formOpen.value = true
}
function onFormSaved() {
  formOpen.value = false
  sch.reload()
}

/* ───────── 스캔 원본 미리보기 ───────── */
const scanOpen = ref(false)
const scanItem = ref(null)
function openScan(item) {
  scanItem.value = item
  scanOpen.value = true
}

/* ───────── 월별 보고서 ───────── */
const reportOpen = ref(false)

/* ───────── 수기 서류 등록 창 (예전 화면의 4단계를 그대로 옮김) ───────── */
const ocrOpen = ref(false)
const ocrFile = ref(null)
function openOcr(file) {
  ocrFile.value = file
  ocrOpen.value = true
}
</script>

<template>
  <div class="inspection-page">
    <TemplateBar />

    <div class="lay">
      <ScheduleCard
        v-model:month="month"
        class="g-cal"
        :items="monthItems"
        :today="today"
        :read-only="readOnly"
        :focus-date="focusDate"
        @add="openAdd"
        @open="openForm"
      />
      <UploadCard class="g-up" :read-only="readOnly" @file="openOcr" />
      <HistoryCard
        v-model:days="days"
        class="g-hist"
        :items="historyItems"
        :today="today"
        :loading="loading"
        :read-only="readOnly"
        @open="openForm"
        @scan="openScan"
        @download="downloadScan"
        @write="openForm(null)"
        @report="reportOpen = true"
      />
    </div>

    <ScheduleAddModal :open="addOpen" :date="addDate" :factory-id="factoryId" @close="addOpen = false" @saved="onAdded" />
    <FormModal
      :open="formOpen"
      :item="formItem"
      :read-only="readOnly"
      :my-name="myName"
      :factory-id="factoryId"
      @close="formOpen = false"
      @saved="onFormSaved"
      @scan="openScan"
    />
    <ScanPreviewModal :open="scanOpen" :item="scanItem" @close="scanOpen = false" />
    <ReportModal :open="reportOpen" :month="month" :today="today" :factory-id="factoryId" @close="reportOpen = false" />
    <OcrRegisterModal :open="ocrOpen" :file="ocrFile" :read-only="readOnly" :my-name="myName" @close="ocrOpen = false" @changed="sch.reload" />
  </div>
</template>

<style scoped>
/*
 * 한 화면에 들어오게: 화면 높이 - 상단 바 - 위아래 여백 (통계 · 이력 화면과 같은 방식)
 * 배치 A안: 왼쪽 900 (달력 | 서류 등록 168) · 오른쪽 나머지 (점검 이력)
 */
.inspection-page {
  height: max(760px, calc(var(--screen-h) - var(--topbar-height) - 40px));
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lay {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 900px) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr) 168px;
  gap: 16px;
}

.g-cal {
  grid-area: 1 / 1;
}

.g-up {
  grid-area: 2 / 1;
}

.g-hist {
  grid-area: 1 / 2 / 3 / 3;
}
</style>
