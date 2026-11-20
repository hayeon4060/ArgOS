<!--
  월별 보고서 창 (화면설계안 v-rev3.js statReport())
  - 왼쪽: 달 목록 (지난달까지, 이번 달은 '작성 중' — 누르면 '…일에 발행됩니다')
  - 오른쪽: 보고서 첫 쪽 미리보기 (요약 4칸, 공장별 안전 등급, 주요 경보)
  - 공장별 등급(A·B·C)은 대시보드에 없고 이 보고서에만 나옴 (설계안 안내 문구)
  - PDF 내려받기: 명세서 GET /reports/monthly — 백엔드에 아직 없어 예시 모드에서는 안내만 (backend-notes 12장)
-->
<script setup>
import { ref, watch } from 'vue'
import { downloadMonthlyReport, getMonthlyReport, getMonthlyReports } from '@/api/stats'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  factoryId: { type: [Number, String], default: '' },
})

const emit = defineEmits(['close'])

const months = ref([])
const month = ref('')
const report = ref(null)
const loading = ref(false)
const busy = ref(false)

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

async function download() {
  busy.value = true
  try {
    const blob = await downloadMonthlyReport({ month: month.value, factoryId: props.factoryId })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `ArgOS_월별보고서_${month.value}.pdf`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast(`ArgOS_월별보고서_${month.value}.pdf 파일을 저장했습니다.`)
  } catch (error) {
    toast(error.message, { type: error.code === 'NOT_READY' ? 'info' : 'err' })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="1180" label="월별 보고서" @close="emit('close')">
        <div class="m-head">
          <h2>월별 보고서</h2>
          <span class="loc">매달 1일 자동 발행 · 내려받기만 할 수 있습니다</span>
          <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
        </div>

        <div class="wrap">
          <!-- 달 목록 -->
          <nav class="months" aria-label="보고서 달">
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

          <!-- 첫 쪽 미리보기 -->
          <div class="preview" :class="{ busy: loading }">
            <article v-if="report" :key="report.month" class="page pop-in">
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
              <footer class="rf"><span>ArgOS · 자동 생성 문서 · 수정할 수 없습니다</span><span class="mono">1 / 3</span></footer>
            </article>
          </div>
        </div>

        <div class="m-actions foot">
          <span class="size">PDF · A4 3쪽</span>
          <button type="button" class="btn" @click="emit('close')">닫기</button>
          <button type="button" class="btn btn-primary" :disabled="busy || !month" @click="download">
            <span v-if="busy" class="m-spin" /><AppIcon v-else name="download" :size="16" />{{ busy ? '내려받는 중' : 'PDF 내려받기' }}
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
  justify-content: center;
  padding: 24px;
  background: #eef2f6;
  transition: opacity 150ms;
}

.preview.busy {
  opacity: 0.4;
}

.page {
  width: 560px;
  min-height: 700px;
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
  margin-top: 24px;
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
