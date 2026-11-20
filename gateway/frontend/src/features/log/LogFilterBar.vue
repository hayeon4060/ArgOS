<!--
  이용 로그 조건 줄 (화면설계안 vLogs() #fb)
  - 기간(오늘 / 7일 / 30일) + 날짜 범위 표시, 공장, 공정, 유형, 검색(아이디 · 이름 · 내용), CSV로 내보내기, 조회
  - 설계안처럼 기간 · 공장 · 공정 · 유형을 바꾸면 바로 다시 조회 (화면이 v-model 값이 바뀌는 것을 보고 부름)
  - 검색은 입력이 멈추고 0.3초 뒤 search(글자)
  - 공장 담당자: 공장 고정 (lockFactory)
-->
<script setup>
import { computed } from 'vue'
import { LOG_GROUPS, PERIODS } from './logMeta'
import AppIcon from '@/components/AppIcon.vue'
import SegButtons from '@/components/SegButtons.vue'

const props = defineProps({
  /** [{ id, name, processes: [{ id, name }] }] */
  factories: { type: Array, default: () => [] },
  lockFactory: { type: Boolean, default: false },
  /** { from, to } 날짜 범위 표시 */
  range: { type: Object, required: true },
  busy: { type: Boolean, default: false },
  exporting: { type: Boolean, default: false },
})

const period = defineModel('period', { type: String, required: true })
const factoryId = defineModel('factoryId', { type: String, default: '' })
const processId = defineModel('processId', { type: String, default: '' })
const group = defineModel('group', { type: String, default: '' })

const emit = defineEmits(['search', 'query', 'export'])

const processes = computed(() => props.factories.find((f) => String(f.id) === factoryId.value)?.processes ?? [])

/** 공장을 바꾸면 공정은 '전체 공정'으로 */
function onFactory(e) {
  processId.value = ''
  factoryId.value = e.target.value
}

let timer = 0
function onInput(e) {
  clearTimeout(timer)
  timer = setTimeout(() => emit('search', e.target.value), 300)
}
</script>

<template>
  <div class="fbar">
    <label>기간</label>
    <SegButtons v-model="period" :options="PERIODS" />
    <span class="range mono"><AppIcon name="cal" :size="15" />{{ range.from }} ~ {{ range.to }}</span>

    <label>공장</label>
    <select :value="factoryId" :disabled="lockFactory" aria-label="공장" @change="onFactory">
      <option v-if="!lockFactory" value="">전체 공장</option>
      <option v-for="f in factories" :key="f.id" :value="String(f.id)">{{ f.name }}</option>
    </select>

    <label>공정</label>
    <select v-model="processId" :disabled="!factoryId" aria-label="공정">
      <option value="">전체 공정</option>
      <option v-for="p in processes" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
    </select>

    <label>유형</label>
    <select v-model="group" aria-label="유형">
      <option value="">전체 유형</option>
      <option v-for="g in LOG_GROUPS" :key="g.key" :value="g.key">{{ g.label }}</option>
    </select>

    <label class="search">
      <AppIcon name="search" :size="16" />
      <input type="search" placeholder="아이디, 이름, 내용 검색" aria-label="이용 로그 검색" @input="onInput" />
    </label>

    <span class="right">
      <button type="button" class="btn" :disabled="exporting" @click="emit('export')">
        <span v-if="exporting" class="m-spin dark" /><AppIcon v-else name="download" :size="16" />{{ exporting ? '파일 만드는 중' : 'CSV로 내보내기' }}
      </button>
      <button type="button" class="btn btn-primary query" :disabled="busy" @click="emit('query')">
        <span v-if="busy" class="m-spin" /><AppIcon v-else name="search" :size="16" />{{ busy ? '조회 중' : '조회' }}
      </button>
    </span>
  </div>
</template>

<style scoped>
.fbar {
  position: relative;
  z-index: 2;
  min-height: 60px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 11px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  white-space: nowrap;
}

label {
  margin-left: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}

label:first-child {
  margin-left: 0;
}

select,
.range {
  height: 36px;
  min-width: 0;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 13.5px;
  color: var(--color-text);
}

select:disabled {
  background: var(--color-surface-sub);
  color: var(--color-text-sub);
}

/* 날짜 범위는 보기만 (설계안 pointer-events:none) */
.range {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-surface-sub);
  color: var(--color-text-sub);
}

.range svg {
  color: var(--color-text-weak);
}

.search {
  width: 240px;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--color-text-weak);
}

.search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 13.5px;
  color: var(--color-text);
}

.right {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.right .btn {
  height: 36px;
}

.query {
  padding: 0 20px;
}

/* 흰 단추 안 돌아가는 고리 (alert.css .m-spin 은 흰색) */
.m-spin.dark {
  border-color: rgba(15, 23, 42, 0.18);
  border-top-color: var(--color-text-sub);
}
</style>
