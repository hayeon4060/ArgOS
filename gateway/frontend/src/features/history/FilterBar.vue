<!--
  조건 줄 (화면설계안 vStats() #fb)
  - 기간: 오늘 / 7일 / 30일 / 직접 선택 + 날짜 범위 (누르면 달력 두 개)
  - 공장 · 공정 · 센서 · 등급 · 근무조 선택 → 바꾸면 바로 조회 (설계안 그대로)
  - 오른쪽: 월별 보고서 · 내보내기 · 조회
  - 공장 담당자는 공장이 담당 공장으로 고정 (화면설계서 '공장 담당자' 상태)
  - 값은 직접 고치지 않고 change(바꾼 값) 로 화면에 알림 → 화면이 조건을 바꾸고 조회
-->
<script setup>
import { computed, ref } from 'vue'
import { LEVELS, MAX_RANGE_DAYS, PERIODS, SENSOR_TYPES, SHIFTS, daysBetween } from './historyFilter'
import AppIcon from '@/components/AppIcon.vue'
import SegButtons from '@/components/SegButtons.vue'

const props = defineProps({
  /** 조건 { period, from, to, factoryId, processId, sensorType, level, shift } */
  filter: { type: Object, required: true },
  /** 공장 목록 [{ id, name, processes: [{ id, name }] }] */
  factories: { type: Array, default: () => [] },
  /** 공장 담당자: 공장 선택 잠금 */
  lockFactory: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'query', 'export', 'report'])

const factory = computed(() => props.factories.find((f) => f.id === Number(props.filter.factoryId)))
const processes = computed(() => factory.value?.processes ?? [])
const rangeText = computed(() => (props.filter.from === props.filter.to ? props.filter.from : `${props.filter.from} ~ ${props.filter.to}`))

/** 기간 단추: 직접 선택이면 달력부터, 나머지는 바로 조회 */
const period = computed({
  get: () => props.filter.period,
  set: (v) => (v === 'custom' ? openPicker() : emit('change', { period: v })),
})

/* ───────── 직접 선택 달력 ───────── */
const picker = ref(false)
const d1 = ref('')
const d2 = ref('')
const pickError = ref('')

function openPicker() {
  d1.value = props.filter.from
  d2.value = props.filter.to
  pickError.value = ''
  picker.value = true
}

function applyPicker() {
  if (!d1.value || !d2.value || d1.value > d2.value) {
    pickError.value = '시작일이 종료일보다 늦습니다.'
    return
  }
  if (daysBetween(d1.value, d2.value) > MAX_RANGE_DAYS) {
    pickError.value = `최대 ${MAX_RANGE_DAYS}일까지 조회할 수 있습니다.`
    return
  }
  picker.value = false
  emit('change', { period: 'custom', from: d1.value, to: d2.value })
}

/** 공장을 바꾸면 공정은 '전체 공정'으로 (화면설계서) */
const onFactory = (e) => emit('change', { factoryId: e.target.value, processId: '' })
</script>

<template>
  <div class="fbar">
    <label>기간</label>
    <SegButtons v-model="period" :options="PERIODS" />
    <div class="range-wrap">
      <button type="button" class="range mono" aria-label="기간 직접 선택" @click="openPicker">
        <AppIcon name="cal" :size="16" />{{ rangeText }}
      </button>
      <!-- 달력 두 개 (바깥을 누르면 닫힘) -->
      <template v-if="picker">
        <div class="backdrop" @click="picker = false" />
        <div class="pop pop-in" role="dialog" aria-label="기간 직접 선택">
          <b>기간 직접 선택</b>
          <div class="dates">
            <input v-model="d1" type="date" aria-label="시작일" />
            <span>~</span>
            <input v-model="d2" type="date" aria-label="종료일" />
          </div>
          <p class="err">
            <template v-if="pickError"><AppIcon name="alert" :size="14" />{{ pickError }}</template>
          </p>
          <div class="pop-act">
            <button type="button" class="btn btn-primary" @click="applyPicker">적용</button>
          </div>
        </div>
      </template>
    </div>

    <label>공장</label>
    <select :value="filter.factoryId" :disabled="lockFactory" aria-label="공장" @change="onFactory">
      <option v-if="!lockFactory" value="">전체 공장</option>
      <option v-for="f in factories" :key="f.id" :value="String(f.id)">{{ f.name }}</option>
    </select>

    <label>공정</label>
    <select :value="filter.processId" :disabled="!factory" aria-label="공정" @change="emit('change', { processId: $event.target.value })">
      <option value="">전체 공정</option>
      <option v-for="p in processes" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
    </select>

    <label>센서</label>
    <select :value="filter.sensorType" aria-label="센서" @change="emit('change', { sensorType: $event.target.value })">
      <option value="">전류 · 온도 · CO</option>
      <option v-for="s in SENSOR_TYPES" :key="s.value" :value="s.value">{{ s.label }}</option>
    </select>

    <label>등급</label>
    <select :value="filter.level" aria-label="등급" @change="emit('change', { level: $event.target.value })">
      <option v-for="l in LEVELS" :key="l.value" :value="l.value">{{ l.label }}</option>
    </select>

    <label>근무조</label>
    <select :value="filter.shift" aria-label="근무조" @change="emit('change', { shift: $event.target.value })">
      <option v-for="s in SHIFTS" :key="s.value" :value="s.value">{{ s.label }}</option>
    </select>

    <span class="right">
      <button type="button" class="btn" @click="emit('report')"><AppIcon name="file" :size="16" />월별 보고서</button>
      <button type="button" class="btn" @click="emit('export')"><AppIcon name="download" :size="16" />내보내기</button>
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
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
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

.range {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.range svg {
  color: var(--color-text-weak);
}

.range-wrap {
  position: relative;
}

.backdrop {
  position: fixed;
  inset: 0;
  z-index: 3;
}

.pop {
  position: absolute;
  top: 44px;
  left: 0;
  z-index: 4;
  width: 330px;
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.pop b {
  font-size: 13px;
}

.dates {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  color: var(--color-text-weak);
}

.dates input {
  flex: 1;
  height: 34px;
  padding: 0 6px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  color: var(--color-text);
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

.pop-act {
  display: flex;
  justify-content: flex-end;
}

.pop-act .btn {
  height: 34px;
  padding: 0 16px;
}

.right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.right .btn {
  height: 36px;
}

.query {
  padding: 0 20px;
}

/* 1700px 보다 좁으면 두 줄로 (선택 상자가 찌그러지지 않게 오른쪽 단추가 아래로 내려감) */
@media (max-width: 1700px) {
  .fbar {
    height: auto;
    min-height: 60px;
    flex-wrap: wrap;
    row-gap: 8px;
    padding: 12px 16px;
  }
}
</style>
