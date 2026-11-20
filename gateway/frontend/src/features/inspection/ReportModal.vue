<!--
  월별 점검 보고서 창 (점검 이력 카드의 '월별 보고서')
  - 고른 달 1일 ~ 말일, 이번 달이면 1일 ~ 오늘까지만 (reportCalc.buildReport)
  - 요약 5칸(대상 · 완료 · 예정 · 누락 · 불량), 설비별 표, 불량 목록, 누락 목록
    (서류 양식별 표는 양식이 F02 하나뿐이라 뺌 — 2026-11-20 변전소 F03 제외)
  - 인쇄: window.print() — 아래 @media print 가 보고서 종이만 남김 (PDF 는 인쇄 창에서 'PDF 로 저장')
  - 데이터: GET /inspections/schedule?month= (달력과 같은 목록) → 별도 보고서 API 없음
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { getSchedule } from '@/api/inspections'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import { SCHEDULE_STATE, monthName, shiftMonth } from './inspectionMeta'
import { buildReport } from './reportCalc'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 처음 보여 줄 달 'YYYY-MM' (달력에서 보고 있던 달) */
  month: { type: String, required: true },
  today: { type: String, required: true },
  factoryId: { type: Number, default: null },
  /** 머리에 쓰는 범위 이름 (예: 울산 5공장) */
  scopeName: { type: String, default: '' },
})

const emit = defineEmits(['close'])

const ym = ref('')
const items = ref([])
const loading = ref(false)
let seq = 0

const rep = computed(() => buildReport(items.value, ym.value, props.today))
const note = computed(() => {
  if (rep.value.future) return ''
  if (rep.value.partial) return `이번 달은 오늘(${rep.value.to.slice(5)})까지만 담습니다.`
  return `${rep.value.from.slice(5)} ~ ${rep.value.to.slice(5)} 전체`
})
const MISS = SCHEDULE_STATE.DELAYED.label

watch(
  () => props.open,
  (open) => {
    if (!open) return
    ym.value = props.month
    load()
  },
)

async function load() {
  const my = ++seq
  loading.value = true
  try {
    const list = await getSchedule({ month: ym.value, factoryId: props.factoryId ?? undefined })
    if (my === seq) items.value = list
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    if (my === seq) loading.value = false
  }
}

function move(n) {
  ym.value = shiftMonth(ym.value, n)
  load()
}

/** 이행 막대 폭 (%) */
const pct = (n, total) => `${total ? (n / total) * 100 : 0}%`
const md = (d) => d.slice(5)

/**
 * 인쇄: body 에 print-insp 를 잠깐 붙여, 아래 @media print 규칙이 이 보고서를 인쇄할 때만 걸리게 함
 * (규칙을 항상 걸어 두면 이 화면을 한 번 연 뒤 다른 화면에서 인쇄해도 빈 종이가 나옴)
 */
function print() {
  document.body.classList.add('print-insp')
  const done = () => document.body.classList.remove('print-insp')
  window.addEventListener('afterprint', done, { once: true })
  window.print()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="900" label="월별 점검 보고서" @close="emit('close')">
        <div class="wrap rep-wrap">
          <div class="m-head rep-noprint">
            <h2>월별 점검 보고서</h2>
            <span class="mnav">
              <button type="button" class="ibtn" aria-label="이전 달" @click="move(-1)"><AppIcon name="cl" :size="15" /></button>
              <b class="mono">{{ monthName(ym) }}</b>
              <button type="button" class="ibtn" aria-label="다음 달" @click="move(1)"><AppIcon name="cr" :size="15" /></button>
            </span>
            <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
          </div>

          <div class="paperwrap rep-paperwrap">
            <div class="paper insp-report">
              <div v-if="rep.future" class="empty">아직 시작하지 않은 달입니다.</div>
              <template v-else>
                <div class="ptop">
                  <div>
                    <small>ArgOS 환경안전관제 · {{ scopeName || '점검 관리' }}</small>
                    <h2>{{ monthName(ym) }} 환기·배기설비 점검 보고서</h2>
                  </div>
                  <span class="code mono">{{ rep.from }} ~ {{ rep.to }}</span>
                </div>

                <div class="rk">
                  <div><small>점검 대상</small><b class="mono">{{ rep.sum.total }}</b></div>
                  <div class="done"><small>완료</small><b class="mono">{{ rep.sum.done }}</b></div>
                  <div><small>예정</small><b class="mono">{{ rep.sum.planned }}</b></div>
                  <div class="bad"><small>{{ MISS }}</small><b class="mono">{{ rep.sum.missed }}</b></div>
                  <div class="bad"><small>불량</small><b class="mono">{{ rep.sum.ng }}</b></div>
                </div>

                <div class="rcols">
                  <div>
                    <h4><i>1</i>설비별</h4>
                    <table class="pt">
                      <thead>
                        <tr><th>설비</th><th class="w42">대상</th><th class="w42">완료</th><th class="w42">{{ MISS }}</th><th class="w42">불량</th><th class="w84">이행</th></tr>
                      </thead>
                      <tbody>
                        <tr v-for="g in rep.byEquipment" :key="g.key">
                          <td>{{ g.label }}</td>
                          <td class="num">{{ g.total }}</td>
                          <td class="num">{{ g.done }}</td>
                          <td class="num" :class="{ ngt: g.missed }">{{ g.missed }}</td>
                          <td class="num" :class="{ ngt: g.ng }">{{ g.ng }}</td>
                          <td><span class="rbar"><i class="ok" :style="{ width: pct(g.done, g.total) }" /><i class="no" :style="{ width: pct(g.missed, g.total) }" /></span></td>
                        </tr>
                        <tr v-if="!rep.byEquipment.length"><td colspan="6" class="c weak">없음</td></tr>
                      </tbody>
                    </table>
                  </div>

                  <div>
                    <h4><i>2</i>불량 {{ rep.ngList.length }}건</h4>
                    <table class="pt">
                      <thead><tr><th class="w56">날짜</th><th>설비</th><th class="w64">점검자</th></tr></thead>
                      <tbody>
                        <tr v-for="it in rep.ngList" :key="it.scheduleId"><td class="c">{{ md(it.date) }}</td><td>{{ it.equipmentName }}</td><td class="c">{{ it.assignee }}</td></tr>
                        <tr v-if="!rep.ngList.length"><td colspan="3" class="c weak">없음</td></tr>
                      </tbody>
                    </table>

                    <h4><i>3</i>{{ MISS }} {{ rep.missedList.length }}건</h4>
                    <table class="pt">
                      <thead><tr><th class="w56">예정일</th><th>설비</th><th class="w64">점검자</th></tr></thead>
                      <tbody>
                        <tr v-for="it in rep.missedList" :key="it.scheduleId"><td class="c">{{ md(it.date) }}</td><td>{{ it.equipmentName }}</td><td class="c">{{ it.assignee }}</td></tr>
                        <tr v-if="!rep.missedList.length"><td colspan="3" class="c weak">없음</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </template>
            </div>
          </div>

          <div class="mf rep-noprint">
            <span class="hint">{{ loading ? '불러오는 중…' : note }}</span>
            <span class="gap" />
            <button type="button" class="btn btn-primary" :disabled="rep.future" @click="print"><AppIcon name="file" :size="15" />인쇄</button>
          </div>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.wrap {
  display: flex;
  flex-direction: column;
  max-height: calc(var(--screen-h) - 48px);
}

.m-head {
  flex: none;
}

.mnav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mnav b {
  min-width: 96px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}

.ibtn {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--color-text-sub);
  cursor: pointer;
}

.ibtn:hover {
  background: var(--color-surface-sub);
}

.paperwrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: #e5e7eb;
}

/* 보고서 종이 (시안 .paper) */
.paper {
  padding: 28px 32px 30px;
  border: 1px solid #cbd5e1;
  background: #fff;
  font-size: 13px;
  color: #111827;
}

.ptop {
  display: flex;
  align-items: flex-end;
  padding-bottom: 10px;
  border-bottom: 2px solid #111827;
}

.ptop small {
  display: block;
  margin-bottom: 4px;
  font-size: 11.5px;
  color: #6b7280;
}

.ptop h2 {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.5px;
}

.code {
  margin-left: auto;
  padding: 4px 12px;
  border: 1.5px solid #111827;
  font-size: 13px;
  font-weight: 600;
}

.rk {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-top: 14px;
}

.rk div {
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
}

.rk small {
  display: block;
  font-size: 11.5px;
  color: #6b7280;
}

.rk b {
  font-size: 22px;
  font-weight: 600;
}

.rk .done b {
  color: var(--color-level-normal-text);
}

.rk .bad b {
  color: var(--color-level-alarm-text);
}

.rcols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}

h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 16px 0 6px;
  font-size: 13px;
  font-weight: 700;
}

h4 i {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  background: #111827;
  color: #fff;
  font-size: 11px;
  font-style: normal;
}

.pt {
  width: 100%;
  border-collapse: collapse;
}

.pt th,
.pt td {
  height: 30px;
  padding: 6px 8px;
  border: 1px solid #6b7280;
}

.pt th {
  background: #f3f4f6;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
}

.w42 { width: 42px; }
.w56 { width: 56px; }
.w64 { width: 64px; }
.w84 { width: 84px; }

.c {
  text-align: center;
}

.num {
  text-align: right;
  font-family: var(--font-mono);
}

.ngt {
  color: var(--color-level-alarm-text);
  font-weight: 700;
}

.weak {
  color: #6b7280;
}

.rbar {
  display: flex;
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: #e5e7eb;
}

.rbar i {
  display: block;
  height: 100%;
}

.rbar .ok {
  background: var(--color-level-normal);
}

.rbar .no {
  background: var(--color-level-alarm);
}

.empty {
  padding: 60px 0;
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

<style>
/*
 * 인쇄: '인쇄' 버튼을 누른 동안(body.print-insp)만, 보고서 종이 한 장만 남김
 * - 화면(#app) · 어두운 배경 · 창 머리/아래 버튼은 숨기고, 창 틀(고정 위치 · 높이 제한 · 스크롤)을 풀어 종이가 잘리지 않게
 * - 창은 body 바로 아래(Teleport)에 있어 > 로 고름. scoped 가 아니어야 body 에 걸림
 */
@media print {
  body.print-insp {
    background: #fff;
  }

  body.print-insp > *:not(.modal-root) {
    display: none !important;
  }

  body.print-insp .modal-root {
    position: static;
    display: block;
    padding: 0;
  }

  body.print-insp .modal-root > .dim,
  body.print-insp .rep-noprint {
    display: none !important;
  }

  body.print-insp .modal-root > [role='dialog'] {
    width: auto !important;
    max-height: none;
    overflow: visible;
    border-radius: 0;
    box-shadow: none;
    animation: none;
  }

  body.print-insp .rep-wrap {
    max-height: none;
  }

  body.print-insp .rep-paperwrap {
    overflow: visible;
    padding: 0;
    background: none;
  }

  body.print-insp .insp-report {
    border: none;
  }
}
</style>
