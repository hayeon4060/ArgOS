<!--
  기준 띠 (화면설계안 vThreshold() setBar() · .rb)
  - 0 ~ 눈금 끝: 초록(정상) · 주황(주의) · 빨강(경보) 구간, 주의·경보 눈금과 숫자
  - 값을 바꾸면 눈금이 0.3초에 옮겨 가고 파랗게, 원래 주의 자리에는 회색 눈금이 남음
  - 위쪽 '현재 22.4' 와 아래 화살표: 지금 값 위치
  - 눈금 끝(max): 응답의 sensors[].max (명세서 · 예시 데이터에만 있음).
    없으면(지금 백엔드 응답) 저장된 경보 기준 · 주의 기준 · 현재값 중 큰 값의 1.25배를 보기 좋은 수로 올려 씀 (backend-notes 11장)
-->
<script setup>
import { computed } from 'vue'
import { fmt } from './thresholdForm'

const props = defineProps({
  /** 눈금 끝 (없으면 기준값으로 계산) */
  max: { type: Number, default: null },
  /** 저장된 기준 */
  saved: { type: Object, required: true },
  /** 입력 중인 기준 (숫자가 아니면 저장된 값으로) */
  warn: { type: Number, default: null },
  alarm: { type: Number, default: null },
  value: { type: Number, default: null },
  dec: { type: Number, default: 0 },
})

/** 보기 좋은 수로 올림: 33.75 → 35, 225 → 250, 37.5 → 40 (가장 큰 자리의 절반 단위) */
function niceCeil(x) {
  if (!(x > 0)) return 1
  const step = 10 ** Math.floor(Math.log10(x)) / 2
  return Math.ceil(x / step) * step
}

/** 눈금 끝: 응답 값이 있으면 그대로, 없으면 기준값 · 현재값으로 계산 */
const top = computed(() => {
  if (Number.isFinite(props.max) && props.max > 0) return props.max
  return niceCeil(Math.max(props.saved.alarm ?? 0, props.saved.warn ?? 0, props.value ?? 0) * 1.25)
})

const pct = (v) => `${Math.max(0, Math.min(100, (v / top.value) * 100)).toFixed(2)}%`
const w = computed(() => props.warn ?? props.saved.warn)
const a = computed(() => props.alarm ?? props.saved.alarm)
const warnChanged = computed(() => w.value !== props.saved.warn)
const alarmChanged = computed(() => a.value !== props.saved.alarm)
</script>

<template>
  <div class="rb">
    <div class="bar">
      <i :style="{ width: pct(Math.min(w, a)), background: '#bbf7d0' }" />
      <i :style="{ width: pct(Math.max(0, a - w)), background: '#fed7aa' }" />
      <i :style="{ flex: 1, background: '#fecaca' }" />
    </div>
    <!-- 원래 주의 자리 (바꿨을 때만 회색으로 남음) -->
    <span class="tk" :style="{ left: pct(saved.warn), background: '#cbd5e1', opacity: warnChanged ? 1 : 0 }" />
    <span class="tk" :style="{ left: pct(w), background: warnChanged ? 'var(--color-primary)' : 'var(--color-level-warn)' }" />
    <span class="tl mono" :style="{ left: pct(w), color: warnChanged ? 'var(--color-primary-hover)' : 'var(--color-level-warn-text)' }">{{ fmt(w) }}</span>
    <span class="tk" :style="{ left: pct(a), background: alarmChanged ? 'var(--color-primary)' : 'var(--color-level-alarm)' }" />
    <span class="tl mono" :style="{ left: pct(a), color: alarmChanged ? 'var(--color-primary-hover)' : 'var(--color-level-alarm-text)' }">{{ fmt(a) }}</span>
    <span v-if="value != null" class="mk" :style="{ left: pct(value) }">현재 {{ value.toFixed(dec) }}</span>
    <span class="ed mono" style="left: 0">0</span>
    <span class="ed mono" style="right: 0">{{ top }}</span>
  </div>
</template>

<style scoped>
.rb {
  position: relative;
  height: 64px;
  margin-top: 6px;
}

.bar {
  position: absolute;
  left: 0;
  right: 0;
  top: 26px;
  height: 12px;
  display: flex;
  border-radius: 2px;
  overflow: hidden;
}

/* 값을 바꾸면 0.3초에 늘고 줄어듦 */
.bar i {
  transition: width 300ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.tk {
  position: absolute;
  top: 21px;
  width: 3px;
  height: 22px;
  margin-left: -1.5px;
  border-radius: 1px;
  transition:
    left 300ms cubic-bezier(0.2, 0.8, 0.2, 1),
    background 200ms,
    opacity 200ms;
}

.tl {
  position: absolute;
  top: 47px;
  transform: translateX(-50%);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  transition:
    left 300ms cubic-bezier(0.2, 0.8, 0.2, 1),
    color 200ms;
}

/* 지금 값 표시 + 아래 화살표 */
.mk {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.mk::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 17px;
  margin-left: -5px;
  border: 5px solid transparent;
  border-top: 7px solid var(--color-text);
}

.ed {
  position: absolute;
  top: 47px;
  font-size: 11.5px;
  color: var(--color-text-weak);
}
</style>
