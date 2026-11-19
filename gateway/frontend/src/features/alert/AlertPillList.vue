<!--
  상단 바 상태 띠 아래로 내려오는 알림 목록 (2026-11-19 재원님 요청)
  - 대응 중인 알림이 여러 건일 때 상태 띠를 누르면 내려옴 → 한 건을 고르면 그 알림 창이 열림
  - 한 줄: 단계 색 띠 · '가스 주의' 같은 유형+단계 · 설비 위치 · 센서 값 · 발생 시각 · 진행 상태
  - 순서: 단계 높은 순(화재 > 경보 > 주의), 같으면 최근 순 (stores/alert.js sortedAlarms)
-->
<script setup>
import { alarmIconOf, hazardLabelOf, levelKeyOf } from './alarmView'
import AppIcon from '@/components/AppIcon.vue'
import { ALARM_STATUS_LABEL, LEVEL, LEVEL_META } from '@/types/codes'

defineProps({
  /** 대응 중인 알림 (GET /alarms 의 items) */
  alarms: { type: Array, required: true },
})

const emit = defineEmits(['pick'])

/** '가스 주의' · '화재 경보' · '화재' (화재 단계는 유형을 다시 붙이지 않음) */
function kindOf(a) {
  const level = LEVEL_META[a.level]?.label ?? ''
  return a.level === LEVEL.FIRE ? level : [hazardLabelOf(a), level].filter(Boolean).join(' ')
}

/** '5공장 · 도장 공정 · 상도 건조로 배기팬' → '도장 공정 · 상도 건조로 배기팬' (공장 이름은 뺌) */
const placeText = (a) => a.location.split(' · ').slice(-2).join(' · ')

/** 진행 상태 '발생 중' · '확인함' · '조치 중 · 김도현 (2/5)' */
function statusText(a) {
  const label = ALARM_STATUS_LABEL[a.status] ?? ''
  if (a.status !== 'ACTING' || !a.checklist) return label
  return `${label} · ${a.assignee ?? ''} (${a.checklist.doneCount}/${a.checklist.totalCount})`
}
</script>

<template>
  <div class="list" role="menu" aria-label="대응 중인 알림">
    <div class="head">대응 중인 알림 <b>{{ alarms.length }}건</b><small>한 건을 고르면 창이 열립니다</small></div>
    <button v-for="a in alarms" :key="a.id" type="button" class="row" :class="`is-${levelKeyOf(a)}`" role="menuitem" @click="emit('pick', a)">
      <span class="ico"><AppIcon :name="a.level === LEVEL.FIRE ? 'flame' : alarmIconOf(a)" :size="17" /></span>
      <span class="main">
        <b><span class="kind">{{ kindOf(a) }}</span>{{ placeText(a) }}</b>
        <small class="mono">{{ a.sensorTag }} · {{ a.value }} {{ a.unit }} · {{ a.raisedAt.slice(11, 19) }}</small>
      </span>
      <span class="status">{{ statusText(a) }}</span>
      <AppIcon name="cr" :size="16" class="go" />
    </button>
  </div>
</template>

<style scoped>
.list {
  width: 520px;
  max-width: calc(100vw - 32px);
  max-height: min(60vh, 440px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid var(--color-border);
  border-top: none;
  border-radius: 0 0 var(--radius-md) var(--radius-md);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18);
}

.head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 11px 16px 9px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.head b {
  color: var(--color-text);
}

.head small {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-weak);
}

/* 한 줄: 왼쪽 단계 색 띠 (주의 주황, 경보·화재 빨강) */
.row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px 11px 16px;
  border: none;
  border-top: 1px solid #f1f5f9;
  background: none;
  text-align: left;
  cursor: pointer;
  box-shadow: inset 4px 0 0 var(--tone);
  transition: background var(--m-hover);
}

.row.is-warn {
  --tone: var(--color-level-warn);
  --soft: rgba(255, 237, 213, 0.55);
}

.row.is-alarm,
.row.is-fire {
  --tone: var(--color-level-alarm);
  --soft: rgba(254, 226, 226, 0.55);
}

.row:hover,
.row:focus-visible {
  background: var(--soft);
  outline: none;
}

.ico {
  flex: none;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--soft);
  color: var(--tone);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.main b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--color-text);
}

.kind {
  margin-right: 8px;
  color: var(--tone);
}

.main small {
  font-size: 12px;
  color: var(--color-text-sub);
}

.status {
  flex: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.go {
  flex: none;
  color: var(--color-text-weak);
}
</style>
