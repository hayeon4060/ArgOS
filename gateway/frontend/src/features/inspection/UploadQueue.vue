<!--
  올리기 영역 + 등록 목록 (화면설계안 vInspect() queue())
  - 끌어다 놓기 또는 '파일 선택' (PDF · JPG · PNG, 20MB 까지) → 틀리면 아래 빨간 문구
  - sampleOnClick: 예시 모드에서 올리기 영역을 누르면 예시 파일을 올린 것으로 (설계안과 같음, 파일 선택 창 없이 확인용)
  - 등록 목록: 서류 종류 · 파일 이름 · 설명 · 상태 칩, 인식 중이면 진행 막대, 새로 올린 줄은 나타나며 들어옴
  - '날짜별 전체 보기' → 등록 목록 창
  - readOnly(본사 관리자): 올리기 영역 대신 안내
-->
<script setup>
import { ref } from 'vue'
import { shortTime, statusChip } from './inspectionMeta'
import { FILE_ACCEPT, FILE_MAX } from '@/api/inspections'
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  /** GET /inspections 의 items */
  rows: { type: Array, default: () => [] },
  currentId: { type: Number, default: null },
  readOnly: { type: Boolean, default: false },
  sampleOnClick: { type: Boolean, default: false },
})

const emit = defineEmits(['file', 'sample', 'open', 'all'])

const fin = ref(null)
const over = ref(false)
const error = ref('')

/** 파일 검사 (명세서: PDF · JPG · PNG, 20MB 이하) */
function take(file) {
  error.value = ''
  if (!file) return
  if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) {
    error.value = 'PDF, JPG, PNG 파일만 올릴 수 있습니다.'
    return
  }
  if (file.size > FILE_MAX) {
    error.value = '20MB를 넘습니다. 파일을 줄여서 올려 주세요.'
    return
  }
  emit('file', file)
}

function onDrop(e) {
  over.value = false
  take(e.dataTransfer.files[0])
}
function onPick() {
  take(fin.value.files[0])
  fin.value.value = '' // 같은 파일을 다시 골라도 동작하게
}
function onZone(sampleOnClick) {
  if (sampleOnClick) emit('sample')
  else fin.value.click()
}

/** 줄 설명: 인식 중 · 공정 · 확인 필요 n개 · 저장 시각 */
function desc(r) {
  if (r.status === 'UPLOADING') return '올리는 중'
  if (r.status === 'RECOGNIZING') return '문자 인식 중'
  if (r.status === 'FAILED') return '인식 실패'
  if (r.status === 'DONE') return `${shortTime(r.reviewedAt || r.uploadedAt)} 저장`
  // 서버 목록 줄에는 needCheckCount 가 없음 → 없으면 '검수 대기'만 (TODO(백엔드): docs/api/frontend-api-audit-1120.md 6장)
  const check = r.needCheckCount == null ? '검수 대기' : r.needCheckCount ? `확인 필요 ${r.needCheckCount}개` : '확인 필요 없음'
  return `${r.processName ?? '공정 미지정'} · ${check}`
}
</script>

<template>
  <div class="qu">
    <template v-if="!readOnly">
      <div
        class="drop"
        :class="{ over }"
        role="button"
        tabindex="0"
        aria-label="스캔한 파일 올리기"
        @click="onZone(sampleOnClick)"
        @keydown.enter="onZone(sampleOnClick)"
        @dragover.prevent="over = true"
        @dragleave="over = false"
        @drop.prevent="onDrop"
      >
        <span class="c"><AppIcon name="upload" :size="28" /></span>
        <b>스캔한 파일을 여기에 끌어다 놓기</b>
        <small>PDF, JPG, PNG · 서류 종류는 자동으로 구분</small>
        <button type="button" class="dlk" @click.stop="fin.click()">파일 선택</button>
      </div>
      <input ref="fin" type="file" :accept="FILE_ACCEPT" hidden aria-label="파일 선택" @change="onPick" />
      <p class="err" role="alert">
        <template v-if="error"><AppIcon name="alert" :size="14" />{{ error }}</template>
      </p>
    </template>
    <p v-else class="ro"><AppIcon name="info" :size="15" />본사 관리자는 서류를 올리거나 고칠 수 없고, 등록된 서류와 현황만 볼 수 있습니다.</p>

    <div class="qh">
      등록 목록
      <button type="button" class="all" @click="emit('all')">날짜별 전체 보기<AppIcon name="cr" :size="14" /></button>
    </div>
    <TransitionGroup name="qi" tag="div" class="list">
      <button
        v-for="r in rows"
        :key="r.id ?? r.fileName"
        type="button"
        class="qi"
        :class="{ on: r.id === currentId }"
        @click="emit('open', r)"
      >
        <span class="code mono">{{ r.formType || '–' }}</span>
        <span class="tx">
          <b>{{ r.fileName }}</b>
          <small>{{ desc(r) }}<span v-if="r.status === 'RECOGNIZING'" class="mono"> {{ r.progress ?? 0 }}%</span></small>
          <span v-if="r.status === 'RECOGNIZING' || r.status === 'UPLOADING'" class="bar"><u :style="{ width: `${r.progress ?? 0}%` }" /></span>
        </span>
        <span class="m-chip" :class="statusChip(r.status, r.id === currentId).cls">{{ statusChip(r.status, r.id === currentId).label }}</span>
      </button>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.qu {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.drop {
  height: 136px;
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1.5px dashed #93c5fd;
  border-radius: var(--radius-sm);
  background: #f8fbff;
  text-align: center;
  cursor: pointer;
  transition:
    background 150ms,
    border-color 150ms,
    transform 150ms;
}

.drop:hover,
.drop.over {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.drop.over {
  transform: scale(1.02);
}

.c {
  color: var(--color-primary);
}

.drop b {
  font-size: 14.5px;
  font-weight: 600;
}

.drop small {
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.dlk {
  margin-top: 2px;
  padding: 0;
  border: none;
  background: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.err {
  min-height: 18px;
  margin: 6px 0 0;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--color-level-alarm-text);
}

.ro {
  display: flex;
  gap: 6px;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  font-size: 12.5px;
  line-height: 18px;
  color: var(--color-text-sub);
}

.ro svg {
  flex: none;
  margin-top: 2px;
  color: var(--color-primary);
}

.qh {
  height: 46px;
  flex: none;
  display: flex;
  align-items: center;
  margin-top: 4px;
  border-bottom: 1px solid var(--color-border);
  font-size: 14.5px;
  font-weight: 700;
}

.all {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: none;
  background: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-primary);
  cursor: pointer;
}

.list {
  flex: 1;
  overflow-y: auto;
}

.qi {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 8px;
  border: none;
  border-bottom: 1px solid #f1f5f9;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background 120ms;
}

.qi:hover {
  background: var(--color-surface-sub);
}

.qi.on,
.qi.on:hover {
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.code {
  width: 30px;
  flex: none;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary-hover);
}

.tx {
  flex: 1;
  min-width: 0;
}

.tx b,
.tx small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx b {
  font-size: 13.5px;
  font-weight: 600;
}

.tx small {
  margin-top: 3px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.bar {
  display: block;
  height: 4px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 2px;
  background: #e2e8f0;
}

.bar u {
  display: block;
  height: 100%;
  background: var(--color-primary);
  transition: width 400ms linear;
}

.m-chip {
  flex: none;
}

/* 새로 올린 줄: 위에서 나타나며 들어옴 */
.qi-enter-active {
  transition:
    opacity 300ms,
    transform 300ms;
}

.qi-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}

.qi-move {
  transition: transform 300ms;
}
</style>
