<!--
  월별 보고서 창 (화면설계안 v-rev3.js statReport())
  - 왼쪽: 달 목록 (지난달까지, 이번 달은 '작성 중' — 누르면 '…일에 발행됩니다')
  - 오른쪽: 보고서 미리보기 — 전체 쪽을 세로로 이어 보여 줌 (스크롤)
    1쪽: 요약 4칸, 공장별 안전 등급, 주요 경보 / 2쪽부터: 그달 알림 내역 전체 (한 쪽에 ROWS 줄)
  - 공장별 등급(A·B·C)은 대시보드에 없고 이 보고서에만 나옴 (설계안 안내 문구)
  - PDF 로 저장: window.print() — 아래 @media print 가 미리보기 종이만 A4 크기로 남김 (인쇄 창에서 'PDF 로 저장')
    → 저장한 파일이 미리보기와 쪽 수 · 모양까지 같음 (백엔드 PDF GET /reports/monthly 는 양식이 달라 안 씀)
  - 미리보기 내용 · 고를 수 있는 달 목록은 서버 API 가 없어 실제 서버에서도 예시 값 (docs/api/frontend-api-audit-1120.md 5장)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { getMonthlyReport, getMonthlyReports } from '@/api/stats'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])

const months = ref([])
const month = ref('')
const report = ref(null)
const loading = ref(false)

/** 알림 내역 한 쪽에 들어가는 줄 수 (종이 높이 792px 기준 — 줄 높이를 바꾸면 같이 맞출 것) */
const ROWS = 24
/** 2쪽부터의 알림 내역 [[알림, ...], ...] */
const alarmPages = computed(() => {
  const list = report.value?.alarms ?? []
  return Array.from({ length: Math.ceil(list.length / ROWS) }, (_, i) => list.slice(i * ROWS, (i + 1) * ROWS))
})
const pageCount = computed(() => 1 + alarmPages.value.length)
const LEVEL = { WARN: '주의', ALARM: '경보', FIRE: '화재' }
const mdhm = (at) => at.slice(5, 16).replace('T', ' ')

const label = (m) => `${m.slice(0, 4)}년 ${Number(m.slice(5))}월`
const md = (d) => `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일`
const GRADE_COLOR = { A: 'var(--color-level-normal-text)', B: 'var(--color-level-warn-text)', C: 'var(--color-level-alarm-text)' }

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    months.value = await getMonthlyReports()
    const first = months.value.find((m) => m.ready)
    if (first) select(first)
  },
)

async function select(m) {
  if (!m.ready) {
    toast(`이번 달 보고서는 ${md(m.willIssueAt)}에 발행됩니다.`, { type: 'info' })
    return
  }
  month.value = m.month
  loading.value = true
  try {
    report.value = await getMonthlyReport(m.month)
  } finally {
    loading.value = false
  }
}

/**
 * PDF 로 저장: body 에 print-monthly 를 잠깐 붙여, 아래 @media print 규칙이 이 보고서를 인쇄할 때만 걸리게 함
 * (점검 관리 ReportModal 과 같은 방식)
 */
function print() {
  document.body.classList.add('print-monthly')
  const done = () => document.body.classList.remove('print-monthly')
  window.addEventListener('afterprint', done, { once: true })
  window.print()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="1180" label="월별 보고서" @close="emit('close')">
        <div class="m-head mrep-noprint">
          <h2>월별 보고서</h2>
          <span class="loc">매달 1일 자동 발행 · 저장만 할 수 있습니다</span>
          <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
        </div>

        <div class="wrap mrep-wrap">
          <!-- 달 목록 -->
          <nav class="months mrep-noprint" aria-label="보고서 달">
            <button
              v-for="m in months"
              :key="m.month"
              type="button"
              :class="{ on: m.month === month, dis: !m.ready }"
              @click="select(m)"
            >
              <AppIcon name="file" :size="18" />
              <span><b class="mono">{{ m.month }}</b><small>{{ m.ready ? `${m.issuedAt.slice(5)} 발행` : `작성 중 · ${md(m.willIssueAt)} 발행` }}</small></span>
              <span v-if="!m.ready" class="m-chip gray">작성 중</span>
            </button>
            <p class="note"><AppIcon name="info" :size="14" />공장별 등급(A·B·C)은 대시보드에는 나오지 않고 월별 보고서에서만 확인합니다.</p>
          </nav>

          <!-- 미리보기: 전체 쪽 (인쇄하면 이 종이들이 그대로 A4 한 장씩) -->
          <div class="preview mrep-preview" :class="{ busy: loading }">
            <article v-if="report" :key="report.month" class="page mrep-page pop-in">
              <header class="rh">
                <div>
                  <small>ArgOS 환경안전 통합관제</small>
                  <h3>{{ label(report.month) }} 환경안전 월간 보고서</h3>
                  <span>{{ report.siteName }} · 발행 {{ report.issuedAt }}</span>
                </div>
                <table class="sign">
                  <tr><th>작성</th><th>검토</th><th>승인</th></tr>
                  <tr><td>{{ report.sign.writer }}</td><td>{{ report.sign.reviewer }}</td><td>{{ report.sign.approver }}</td></tr>
                </table>
              </header>

              <div class="rk">
                <div><span>전체 알림</span><b class="mono">{{ report.kpi.alarmCount }}건</b><small>지난달보다 {{ Math.abs(report.kpi.alarmDiff) }}건 {{ report.kpi.alarmDiff > 0 ? '증가' : '감소' }}</small></div>
                <div><span>경보</span><b class="mono">{{ report.kpi.dangerCount }}건</b><small>지난달보다 {{ Math.abs(report.kpi.dangerDiff) }}건 {{ report.kpi.dangerDiff > 0 ? '증가' : '감소' }}</small></div>
                <div><span>평균 조치 시간</span><b class="mono">{{ Math.round(report.kpi.avgActionMinutes ?? 0) }}분</b><small>목표 15분 이내</small></div>
                <div><span>점검 완료율</span><b class="mono">{{ report.kpi.checklistDoneRate ?? '–' }}%</b><small>목표 95% 이상</small></div>
              </div>

              <h4>1. 공장별 안전 등급</h4>
              <table class="rt">
                <tr><th>공장</th><th>등급</th><th>점수</th><th>주의</th><th>경보</th><th>평균 조치</th></tr>
                <tr v-for="g in report.grades" :key="g.factory">
                  <td>{{ g.factory }}</td>
                  <td><b class="gr" :style="{ background: GRADE_COLOR[g.grade] }">{{ g.grade }}</b></td>
                  <td class="mono">{{ g.score }}</td>
                  <td class="mono">{{ g.warn }}</td>
                  <td class="mono">{{ g.alarm }}</td>
                  <td class="mono">{{ g.avgActionMinutes }}분</td>
                </tr>
              </table>
              <p class="nt">등급 기준: A 90점 이상 · B 75점 이상 · C 75점 미만 (경보 건수, 조치 시간, 점검 완료율 반영)</p>

              <h4>2. 주요 경보와 조치</h4>
              <table class="rt">
                <tr><th>일시</th><th>위치</th><th>내용</th><th>조치</th></tr>
                <tr v-for="a in report.major" :key="a.at">
                  <td class="mono">{{ a.at.slice(5, 16).replace('T', ' ') }}</td>
                  <td>{{ a.location }}</td>
                  <td>{{ a.text }}</td>
                  <td>{{ a.action }}</td>
                </tr>
                <tr v-if="!report.major.length"><td colspan="4">이달에는 경보가 없었습니다.</td></tr>
              </table>
              <footer class="rf"><span>ArgOS · 자동 생성 문서 · 수정할 수 없습니다</span><span class="mono">1 / {{ pageCount }}</span></footer>
            </article>

            <template v-if="report">
              <article v-for="(rows, p) in alarmPages" :key="`${report.month}-${p}`" class="page mrep-page pop-in">
                <h4 class="first">3. 알림 내역 <small>전체 {{ report.alarms.length }}건{{ p ? ' · 이어서' : '' }}</small></h4>
                <table class="rt list">
                  <tr><th class="w34">번호</th><th class="w92">일시</th><th class="w38">등급</th><th>위치</th><th class="w62">내용</th><th class="w50">조치</th></tr>
                  <tr v-for="(a, i) in rows" :key="a.id">
                    <td class="mono">{{ p * ROWS + i + 1 }}</td>
                    <td class="mono">{{ mdhm(a.at) }}</td>
                    <td><b :class="a.level === 'WARN' ? 'lw' : 'la'">{{ LEVEL[a.level] ?? a.level }}</b></td>
                    <td>{{ a.location }}</td>
                    <td>{{ a.text }}</td>
                    <td class="mono">{{ a.actionMinutes != null ? `${a.actionMinutes}분` : '조치 중' }}</td>
                  </tr>
                </table>
                <footer class="rf"><span>ArgOS · 자동 생성 문서 · 수정할 수 없습니다</span><span class="mono">{{ p + 2 }} / {{ pageCount }}</span></footer>
              </article>
            </template>
          </div>
        </div>

        <div class="m-actions foot mrep-noprint">
          <span class="size">A4 {{ pageCount }}쪽 · 인쇄 창에서 'PDF로 저장'을 고르면 미리보기 그대로 저장됩니다</span>
          <button type="button" class="btn" @click="emit('close')">닫기</button>
          <button type="button" class="btn btn-primary" :disabled="!report || loading" @click="print">
            <AppIcon name="download" :size="16" />PDF로 저장
          </button>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.wrap {
  display: flex;
  height: min(760px, calc(var(--screen-h) - 220px));
}

.months {
  width: 272px;
  flex: none;
  padding: 14px 12px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface-sub);
}

.months button {
  width: 100%;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.months button:hover {
  background: #eef2f6;
}

.months button.on {
  background: var(--color-surface);
  box-shadow:
    inset 3px 0 0 var(--color-primary),
    0 1px 3px rgba(15, 23, 42, 0.08);
}

.months button.dis {
  color: var(--color-text-weak);
}

.months svg {
  color: var(--color-text-weak);
}

.months span:not(.m-chip) {
  display: flex;
  flex-direction: column;
}

.months small {
  font-size: 12px;
  color: var(--color-text-weak);
}

.months .m-chip {
  margin-left: auto;
}

.note {
  display: flex;
  gap: 6px;
  margin-top: 14px;
  padding: 10px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  font-size: 12.5px;
  line-height: 18px;
  color: var(--color-text-sub);
}

.note svg {
  flex: none;
  margin-top: 2px;
  color: var(--color-primary);
}

.preview {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: #eef2f6;
  transition: opacity 150ms;
}

.preview.busy {
  opacity: 0.4;
}

/* 종이 한 장: A4 비율 (560 × 792 = 210 × 297mm). 인쇄할 때는 이 크기 그대로 A4 로 키움 */
.page {
  flex: none;
  display: flex;
  flex-direction: column;
  width: 560px;
  height: 792px;
  overflow: hidden;
  padding: 30px 34px 20px;
  background: #fff;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.14);
  font-size: 11.5px;
  color: #334155;
}

.rh {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--color-text);
}

.rh small,
.rh span {
  font-size: 10.5px;
  color: #64748b;
}

.rh h3 {
  margin: 2px 0;
  font-size: 19px;
  letter-spacing: -0.3px;
  color: var(--color-text);
}

.sign {
  border-collapse: collapse;
  font-size: 10.5px;
}

.sign th,
.sign td {
  width: 48px;
  padding: 3px 6px;
  border: 1px solid #cbd5e1;
  text-align: center;
}

.sign td {
  height: 34px;
}

.rk {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 16px 0;
}

.rk div {
  display: flex;
  flex-direction: column;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
}

.rk b {
  font-size: 18px;
  color: var(--color-text);
}

.rk small {
  font-size: 10px;
  color: #64748b;
}

h4 {
  margin: 16px 0 8px;
  font-size: 13px;
  color: var(--color-text);
}

.rt {
  width: 100%;
  border-collapse: collapse;
}

.rt th,
.rt td {
  padding: 5px 8px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
}

.rt th {
  background: #f8fafc;
  font-weight: 600;
}

/* 알림 내역 표: 한 줄 높이를 고정해야 쪽마다 ROWS 줄이 정확히 들어감 */
h4.first {
  margin-top: 0;
}

h4 small {
  margin-left: 6px;
  font-size: 10.5px;
  font-weight: 400;
  color: #64748b;
}

.list {
  table-layout: fixed;
}

.list th,
.list td {
  height: 26px;
  padding: 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.w34 { width: 34px; }
.w38 { width: 38px; }
.w50 { width: 50px; }
.w62 { width: 62px; }
.w92 { width: 92px; }

.lw {
  color: var(--color-level-warn-text);
}

.la {
  color: var(--color-level-alarm-text);
}

.gr {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  color: #fff;
}

.nt {
  margin-top: 6px;
  font-size: 10px;
  color: #64748b;
}

.rf {
  display: flex;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
  font-size: 10px;
  color: #94a3b8;
}

.foot {
  align-items: center;
  padding: 16px 28px 20px 300px;
  border-top: 1px solid var(--color-border);
}

.size {
  font-size: 12.5px;
  color: #64748b;
}

.foot .btn:first-of-type {
  margin-left: auto;
}

.foot .btn {
  height: 44px;
  padding: 0 20px;
}
</style>

<style>
/*
 * 인쇄(PDF 저장): 'PDF로 저장' 버튼을 누른 동안(body.print-monthly)만, 미리보기 종이들만 남김
 * - 화면(#app) · 어두운 배경 · 창 머리 · 달 목록 · 아래 버튼은 숨기고, 창 틀(고정 위치 · 높이 · 스크롤)을 풂
 * - 종이(560 × 792px)를 A4(210 × 297mm = 794 × 1123px)로 그대로 키워 한 장에 한 쪽씩 → 미리보기와 같은 모양
 * - 창은 body 바로 아래(Teleport)에 있어 > 로 고름. scoped 가 아니어야 body 에 걸림
 */
@page mrep {
  size: A4;
  margin: 0;
}

@media print {
  body.print-monthly {
    background: #fff;
  }

  body.print-monthly > *:not(.modal-root) {
    display: none !important;
  }

  body.print-monthly .modal-root {
    position: static;
    display: block;
    padding: 0;
  }

  body.print-monthly .modal-root > .dim,
  body.print-monthly .mrep-noprint {
    display: none !important;
  }

  body.print-monthly .modal-root > [role='dialog'] {
    width: auto !important;
    max-height: none;
    overflow: visible;
    border-radius: 0;
    box-shadow: none;
    animation: none;
  }

  body.print-monthly .mrep-wrap {
    display: block;
    height: auto;
  }

  body.print-monthly .mrep-preview {
    display: block;
    overflow: visible;
    padding: 0;
    background: none;
    opacity: 1;
  }

  body.print-monthly .mrep-page {
    page: mrep;
    zoom: 1.4179;
    break-after: page;
    box-shadow: none;
    animation: none;
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }

  body.print-monthly .mrep-page:last-child {
    break-after: auto;
  }
}
</style>
