<!--
  등록 목록 창 — 날짜별 전체 보기 (화면설계안 v-rev3.js inspectList(), 화면설계서 '등록 목록 팝업')
  - 조건: 등록일 범위(빠른 선택 오늘 / 7일 / 이번 달), 서류 종류, 상태 → 바꾸면 바로 조회
  - 목록: 등록 시각 · 서류 · 파일 · 공정 · 확인 필요 · 등록자 · 상태, 11행씩
  - 줄을 누르면 pick(row) → 화면이 그 서류를 열고 창을 닫음
-->
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { FORM_TYPES, shortTime, statusChip, ymd } from './inspectionMeta'
import { getInspections } from '@/api/inspections'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import SegButtons from '@/components/SegButtons.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 서류별 등록 현황에서 연 경우 그 서류 종류 */
  formType: { type: String, default: '' },
})

const emit = defineEmits(['close', 'pick'])

const PER = 11
const today = new Date()
const QUICK = [
  { value: 'today', label: '금일' },
  { value: '7d', label: '7일' },
  { value: 'month', label: '이번 달' },
]
const STATUS = [
  { value: '', label: '전체' },
  { value: 'REVIEW', label: '검수 대기' },
  { value: 'DONE', label: '저장' },
  { value: 'RECOGNIZING', label: '인식 중' },
]

const f = reactive({ quick: '7d', from: '', to: '', formType: '', status: '', page: 1 })
const rows = ref(null)
const total = ref(0)
const err = ref('')
let seq = 0

function applyQuick(q) {
  const back = (n) => ymd(new Date(today.getFullYear(), today.getMonth(), today.getDate() - n))
  f.to = ymd(today)
  f.from = q === 'today' ? f.to : q === '7d' ? back(6) : ymd(new Date(today.getFullYear(), today.getMonth(), 1))
}

async function load() {
  const my = ++seq
  err.value = ''
  if (f.from > f.to) {
    err.value = '시작일이 종료일보다 늦습니다'
    rows.value = []
    total.value = 0
    return
  }
  rows.value = null
  const r = await getInspections({ from: f.from, to: f.to, formType: f.formType, status: f.status, page: f.page - 1, size: PER })
  if (my !== seq) return
  rows.value = r.items
  total.value = r.total
}

// 열 때마다 처음 조건(최근 7일, 넘겨받은 서류 종류)
watch(
  () => props.open,
  (open) => {
    if (!open) return
    Object.assign(f, { quick: '7d', formType: props.formType, status: '', page: 1 })
    applyQuick('7d')
    load()
  },
)

const quick = computed({
  get: () => f.quick,
  set: (q) => {
    f.quick = q
    applyQuick(q)
    f.page = 1
    load()
  },
})
function onDate() {
  f.quick = ''
  f.page = 1
  load()
}
function onFilter() {
  f.page = 1
  load()
}

const pages = computed(() => Math.max(1, Math.ceil(total.value / PER)))
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="1100" label="등록 목록" @close="emit('close')">
        <div class="m-head">
          <h2>등록 목록</h2>
          <span class="loc">수기 서류 등록 내역</span>
          <button type="button" class="m-close" aria-label="닫기" @click="emit('close')"><AppIcon name="x" /></button>
        </div>

        <div class="ilf">
          <label>등록일</label>
          <input v-model="f.from" type="date" aria-label="시작일" @change="onDate" />
          <span class="tilde">~</span>
          <input v-model="f.to" type="date" aria-label="종료일" @change="onDate" />
          <SegButtons v-model="quick" :options="QUICK" small />
          <label class="gap">서류</label>
          <select v-model="f.formType" aria-label="서류 종류" @change="onFilter">
            <option value="">전체 서류</option>
            <option v-for="t in FORM_TYPES" :key="t.value" :value="t.value">{{ t.value }} {{ t.label }}</option>
          </select>
          <label class="gap">상태</label>
          <select v-model="f.status" aria-label="상태" @change="onFilter">
            <option v-for="s in STATUS" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </div>

        <div class="ilt">
          <div v-if="err" class="noval"><AppIcon name="cal" :size="24" /><b>{{ err }}</b>날짜를 다시 골라 주세요.</div>
          <div v-else-if="!rows" class="sks"><i v-for="i in 9" :key="i" class="sk" /></div>
          <table v-else-if="rows.length" class="tb">
            <thead>
              <tr><th>등록일</th><th>서류</th><th>파일</th><th>공정</th><th>확인 필요</th><th>등록자</th><th class="st">상태</th></tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in rows" :key="r.id" class="row fadein" :style="{ animationDelay: `${i * 25}ms` }" tabindex="0" @click="emit('pick', r)" @keydown.enter="emit('pick', r)">
                <td class="mono">{{ shortTime(r.uploadedAt) }}</td>
                <td><span class="code mono">{{ r.formType }}</span>{{ r.formName }}</td>
                <td class="file">{{ r.fileName }}</td>
                <td>{{ r.processName }}</td>
                <td class="mono">{{ r.status === 'REVIEW' && r.needCheckCount != null ? `${r.needCheckCount}개` : '–' }}</td>
                <td>{{ r.uploadedBy }}</td>
                <td><span class="m-chip" :class="statusChip(r.status).cls">{{ statusChip(r.status).label }}</span></td>
              </tr>
            </tbody>
          </table>
          <div v-else class="noval"><AppIcon name="search" :size="24" /><b>이 기간에 등록한 서류가 없습니다</b>날짜나 서류 종류를 바꿔 보세요.</div>
        </div>

        <div v-if="rows?.length" class="pg">
          <span class="count">{{ total }}건</span>
          <button v-for="p in pages" :key="p" type="button" class="mono" :class="{ on: p === f.page }" @click="(f.page = p), load()">{{ p }}</button>
        </div>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ilf {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.ilf label {
  font-size: 13px;
  color: var(--color-text-sub);
}

.ilf .gap {
  margin-left: 10px;
}

.ilf input,
.ilf select {
  height: 34px;
  padding: 0 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13px;
  color: var(--color-text);
}

.ilf input {
  width: 140px;
}

.tilde {
  color: var(--color-text-weak);
}

.ilt {
  height: min(560px, calc(var(--screen-h) - 300px));
  overflow-y: auto;
}

.tb {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}

th {
  position: sticky;
  top: 0;
  height: 40px;
  padding: 0 12px;
  background: var(--color-surface-sub);
  border-bottom: 1px solid var(--color-border);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-sub);
  text-align: left;
}

td {
  height: 46px;
  padding: 0 12px;
  border-bottom: 1px solid #f1f5f9;
  white-space: nowrap;
}

th:first-child,
td:first-child {
  padding-left: 28px;
}

.st {
  width: 100px;
}

.row {
  cursor: pointer;
  transition: background var(--m-hover);
}

.row:hover,
.row:focus-visible {
  background: var(--color-surface-sub);
}

.code {
  margin-right: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary-hover);
}

.file {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fadein {
  animation: fade-up 300ms ease-out both;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.sks {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 28px;
}

.sk {
  display: block;
  height: 30px;
  border-radius: 4px;
  background: #f1f5f9;
}

.noval {
  height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.noval b {
  font-size: 14px;
  color: var(--color-text-sub);
}

.pg {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  padding: 0 20px;
  border-top: 1px solid var(--color-border);
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.count {
  margin-right: 8px;
}

.pg button {
  min-width: 26px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
}

.pg button.on {
  background: var(--color-primary-soft);
  font-weight: 600;
  color: var(--color-primary-hover);
}

@media (prefers-reduced-motion: reduce) {
  .fadein {
    animation: none;
  }
}
</style>
