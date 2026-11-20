<!--
  센서 한 개 기준값 카드 (화면설계안 vThreshold() 카드 .sc)
  - 머리: 센서 종류 아이콘 · 이름 · 태그, 오른쪽 상태 배지 (바꾸는 중이면 파란 '변경 중')
  - 지금 값(큰 숫자, 단계 색) + 기준 띠(RangeBar)
  - 주의 · 경보 입력 칸: 바꾸면 파란 테두리 + 원래 값 가운데 줄, 오른쪽 '평소의 124%'(전류) · '법정 기준'(CO 경보 30)
  - 위·아래 화살표 키로 0.1(전류) / 1(그 밖) 씩, 칸에 들어가면 글자 전체 선택
  - 아래: 검사 문구(빨강 오류 / 주황 안내), 근거 안내
  - 바꾸는 중이면 카드 테두리가 파랗게 (0.2초)
-->
<script setup>
import { computed } from 'vue'
import RangeBar from './RangeBar.vue'
import { check, fmt, num, savedOf } from './thresholdForm'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { LEVEL, LEVEL_META, sensorTypeOf } from '@/types/codes'

const props = defineProps({
  /** GET /equipment/{id}/thresholds 의 sensors 한 개 */
  sensor: { type: Object, required: true },
  season: { type: String, required: true },
  /** 이 센서 입력 칸 { warn: '21.6', alarm: '27' } */
  draft: { type: Object, required: true },
})

const emit = defineEmits(['input', 'focus'])

const type = computed(() => sensorTypeOf(props.sensor.type))
const saved = computed(() => savedOf(props.sensor, props.season))
const w = computed(() => num(props.draft.warn))
const a = computed(() => num(props.draft.alarm))
const changed = (field) => num(props.draft[field]) !== saved.value[field]
const anyChanged = computed(() => changed('warn') || changed('alarm'))
const problem = computed(() => check(props.sensor, props.draft))

/** 지금 값 단계 (저장된 기준으로) */
const level = computed(() => {
  const v = props.sensor.value
  if (v == null) return LEVEL.NORMAL
  return v >= saved.value.alarm ? LEVEL.ALARM : v >= saved.value.warn ? LEVEL.WARN : LEVEL.NORMAL
})

/** 칸 오른쪽 작은 글자: 전류는 '평소의 n%', CO 경보가 법정 한도면 '법정 기준' */
function hint(field) {
  const v = num(props.draft[field])
  if (v === null) return ''
  if (props.sensor.type === 'CURRENT' && props.sensor.setpoint) return `평소의 ${Math.round((v / props.sensor.setpoint) * 100)}%`
  if (field === 'alarm' && props.sensor.legal && v === props.sensor.legal.max) return '법정 기준'
  return ''
}

const FIELDS = [
  { field: 'warn', label: '주의', color: 'var(--color-level-warn)' },
  { field: 'alarm', label: '경보', color: 'var(--color-level-alarm)' },
]

/** 화살표 키로 한 칸씩 */
function onKey(e, field) {
  if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return
  e.preventDefault()
  const v = num(props.draft[field])
  if (v === null) return
  const step = props.sensor.type === 'CURRENT' ? 0.1 : 1
  emit('input', field, fmt(Math.max(0, v + (e.key === 'ArrowUp' ? step : -step))))
}

function onFocus(e, field) {
  e.target.select()
  emit('focus', field)
}
</script>

<template>
  <section class="sc" :class="{ chg: anyChanged }">
    <h4>
      <AppIcon :name="type?.icon" :size="18" />{{ type?.label }}<span class="mono tag">{{ sensor.tag }}</span>
      <span class="chip-slot">
        <span v-if="anyChanged" class="m-chip info"><AppIcon name="edit" :size="13" />변경 중</span>
        <LevelBadge v-else :level="level" />
      </span>
    </h4>

    <div class="cur mono" :style="{ color: level === LEVEL.NORMAL ? 'var(--color-text)' : LEVEL_META[level].color }">
      {{ sensor.value != null ? sensor.value.toFixed(type?.dec ?? 0) : '–' }}<small>{{ sensor.unit }} · 현재값</small>
    </div>

    <RangeBar :max="sensor.max" :saved="saved" :warn="w" :alarm="a" :value="sensor.value" :dec="type?.dec ?? 0" />

    <div v-for="(f, i) in FIELDS" :key="f.field" class="th" :class="{ first: i === 0 }">
      <span class="k"><i :style="{ background: f.color }" />{{ f.label }}</span>
      <label class="in" :class="{ ed: changed(f.field) }">
        <input
          :value="draft[f.field]"
          inputmode="decimal"
          autocomplete="off"
          :aria-label="`${type?.label} ${f.label} 기준`"
          @input="emit('input', f.field, $event.target.value)"
          @focus="onFocus($event, f.field)"
          @keydown="onKey($event, f.field)"
        />
      </label>
      <span class="u">{{ sensor.unit }} 이상</span>
      <span v-if="changed(f.field)" class="old mono">{{ fmt(saved[f.field]) }}</span>
      <span class="pc">{{ hint(f.field) }}</span>
    </div>

    <div class="msg">
      <span v-if="problem" :class="problem.level" role="alert">
        <AppIcon :name="problem.level === 'warn' ? 'tri' : 'alert'" :size="14" />{{ problem.text }}
      </span>
    </div>
    <div class="nt"><AppIcon name="info" :size="15" /><span>{{ sensor.basis }}</span></div>
  </section>
</template>

<style scoped>
.sc {
  padding: 18px 20px;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  background: #fff;
  transition:
    border-color 200ms,
    box-shadow 200ms;
}

/* 바꾸는 중: 파란 테두리 */
.sc.chg {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
}

h4 > .app-icon {
  color: var(--color-text-sub);
}

.tag {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.chip-slot {
  margin-left: auto;
}

.cur {
  margin-top: 14px;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: -0.5px;
}

.cur small {
  margin-left: 6px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0;
  color: var(--color-text-weak);
}

/* 입력 줄 (설계안 .th) */
.th {
  height: 50px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid #f1f5f9;
  font-size: 14px;
}

.th.first {
  margin-top: 12px;
}

.k {
  width: 64px;
  display: flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
}

.k i {
  width: 9px;
  height: 9px;
  border-radius: 2px;
}

.in {
  width: 104px;
  height: 36px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  cursor: text;
  transition:
    border-color 150ms,
    box-shadow 150ms,
    background 150ms;
}

.in:hover {
  border-color: var(--color-text-weak);
}

.in:focus-within {
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

/* 바꾼 칸: 굵은 파란 테두리 + 옅은 파랑 */
.in.ed {
  padding: 0 11px;
  border: 2px solid var(--color-primary);
  background: var(--color-primary-soft);
}

.in input {
  width: 100%;
  border: none;
  outline: none;
  background: none;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  text-align: right;
}

.u {
  font-size: 13px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.old {
  font-size: 12.5px;
  color: var(--color-text-weak);
  text-decoration: line-through;
}

.pc {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--color-text-weak);
  white-space: nowrap;
}

.msg {
  min-height: 20px;
  margin-top: 2px;
}

.msg span {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
  animation: softin 200ms both;
}

.msg .err {
  color: var(--color-level-alarm-text);
}

.msg .warn {
  color: var(--color-level-warn-text);
}

/* 근거 안내 (설계안 .nt) */
.nt {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: #f8fafc;
  font-size: 12.5px;
  line-height: 19px;
  color: var(--color-text-sub);
}

.nt .app-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-text-weak);
}
</style>
