<!--
  가스(CO) 알림 창의 '배기팬 자동 제어' 한 줄 (2026-11-19 재원님 요청)
  - 주의 알림 창 · 주의 상세 창 · 경보 대응 창에 같이 씀
  - 값은 알림 응답의 fanAuto 그대로 보여 주기만 함 (실제로 팬을 돌리는 건 서버, backend-notes 20장)
    { fanName, mode: 'AUTO'|'MANUAL', percent, rpm, beforeRpm, status, commandId }
    status: APPLIED · SENT · FAILED · SKIPPED · AWAITING(승인 대기) · DENIED(거절)
  - 자동(AUTO): '배기팬 자동 가동 75%' + '900 → 1,350 rpm' (팬 아이콘이 돎)
    수동(MANUAL): 승인 대기(AWAITING + commandId)면 '승인' · '거절' 버튼 (공장 담당자만, 2026-11-20 백엔드 피드백)
      → POST /fan-commands/{commandId}/approve · deny. 본사 관리자는 '승인 대기' 안내만
      그 밖의 수동(예전 SKIPPED 등)은 안내만 ('수동 모드라 바꾸지 않았습니다')
  - 자동/수동은 '설비 기준값 관리' 창 › 설비 설정의 '배기팬 자동 제어' 스위치로 정함 (2026-11-19 설비 정보 카드에서 옮김)
  - 대시보드에서 팬을 손으로 조절하는 창은 2026-11-19 삭제 → 수동·실패 때는 현장 확인 안내
  - 2026-11-20 재원님 요청: 가스 주의 창(adjustable)이면 아래에 속도 직접 조절 20 ~ 60 Hz (FanSpeedControl, 공장 담당자만)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { decideFan } from '@/api/equipment'
import { useAuthStore } from '@/stores/auth'
import { ROLE, RPM_PER_HZ } from '@/types/codes'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'
import FanSpeedControl from './FanSpeedControl.vue'

const props = defineProps({
  /** 알림 응답의 fanAuto (가스 알림이 아니거나 배기팬이 없으면 부모가 그리지 않음) */
  fan: { type: Object, required: true },
  /** 색: 'warn' 주황 / 'alarm' 빨강 */
  tone: { type: String, default: 'warn' },
  /** 속도 직접 조절을 붙일지 (가스 주의 창만 true — 경보 대응 창은 셧다운 · 대피가 먼저라 안 붙임) */
  adjustable: { type: Boolean, default: false },
})

const auth = useAuthStore()

/*
 * 승인 · 거절 결과를 바로 보여 주려고 받은 값을 잠깐 덮어씀
 * - 실제 서버는 처리 뒤 SSE alarm.updated 로 새 fanAuto 를 보내 줌 → 부모가 새 값을 넘기면 덮어쓴 값은 버림
 */
const override = ref(null)
/** 409: 다른 담당자가 먼저 처리했거나 알림 종료로 취소됨 → 결과를 모르니 버튼만 숨기고 서버의 새 값을 기다림 */
const handledElsewhere = ref(false)
watch(
  () => props.fan,
  () => {
    override.value = null
    handledElsewhere.value = false
  },
)
/*
 * 이 창에서 속도를 직접 정한 값 (FanSpeedControl 'sent')
 * - 실제 서버는 아직 알림에 manualHz 를 안 실어 줌 → 다시 불러와도(SSE) 사라지지 않게 따로 둠
 * - 다른 알림(발생 시각이 다름)이 오면 지움
 */
const sentHz = ref(null)
watch(
  () => props.fan.at,
  () => (sentHz.value = null),
)
const f = computed(() => ({ ...props.fan, ...override.value, manualHz: sentHz.value ?? props.fan.manualHz ?? null }))
/** 담당자가 속도를 직접 정했는지 (정했으면 제목 · rpm 을 그 값으로) */
const manual = computed(() => f.value.manualHz != null)

/** 받침에 맞는 '은/는' ('배기팬은', '팬모터는') */
const eunNeun = (word) => {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  return code >= 0 && code <= 11171 && code % 28 ? '은' : '는'
}
/** 받침에 맞는 '을/를' */
const eulReul = (word) => (eunNeun(word) === '은' ? '을' : '를')

const rpmText = (v) => (v == null ? '–' : Number(v).toLocaleString())
const isAuto = computed(() => f.value.mode === 'AUTO')
/** 수동인데 담당자 승인을 기다리는 중 (명령 번호가 있어야 승인 · 거절을 보낼 수 있음) */
const awaiting = computed(() => !isAuto.value && f.value.status === 'AWAITING' && f.value.commandId != null)
/** 수동이지만 승인해서 올린(올리는 중) 상태 */
const approved = computed(() => !isAuto.value && ['SENT', 'APPLIED'].includes(f.value.status))
/** 승인 · 거절 버튼은 공장 담당자만 (본사 관리자는 열람만, 서버도 403) */
const canDecide = computed(() => auth.role === ROLE.PLANT_ADMIN)
/** 회전수 화살표(900 → 1,350)를 보여 줄지: 자동, 승인 대기, 승인한 뒤, 직접 정한 뒤 */
const showRpm = computed(() => isAuto.value || awaiting.value || approved.value || manual.value)
/** 화살표 오른쪽 값: 직접 정했으면 그 값(Hz × 30), 아니면 자동 목표 회전수 */
const targetRpm = computed(() => (manual.value ? f.value.manualHz * RPM_PER_HZ : f.value.rpm))

/** 굵은 제목 줄 */
const title = computed(() => {
  const { status, percent } = f.value
  if (manual.value) return `배기팬 직접 조절 ${f.value.manualHz} Hz`
  if (isAuto.value) {
    if (status === 'FAILED') return `배기팬 ${percent}% 자동 변경 실패`
    if (status === 'SENT') return `배기팬 ${percent}%로 올리는 중`
    return `배기팬 자동 가동 ${percent}%`
  }
  if (awaiting.value) return `배기팬 ${percent}% 가동 승인 대기`
  if (status === 'SENT') return `배기팬 ${percent}%로 올리는 중 (승인)`
  if (status === 'APPLIED') return `배기팬 가동 ${percent}% (승인)`
  if (status === 'DENIED') return '배기팬 가동 거절'
  if (status === 'FAILED') return '배기팬 변경 실패'
  return '배기팬 수동 모드'
})

/** 아래 설명 줄 */
const text = computed(() => {
  const name = f.value.fanName
  const { status } = f.value
  if (manual.value) return `${name} 속도를 담당자가 직접 정했습니다.`
  if (isAuto.value) {
    if (status === 'FAILED') return `${name} 명령이 전달되지 않았습니다. 현장에서 배기팬을 확인해 주세요.`
    return `${name} 회전수를 자동으로 올렸습니다.`
  }
  if (awaiting.value) {
    return canDecide.value
      ? `${name}${eunNeun(name)} 수동입니다. 회전수를 올릴지 승인해 주세요.`
      : `${name}${eunNeun(name)} 수동이라 공장 담당자의 승인을 기다리고 있습니다.`
  }
  if (status === 'SENT') return `승인했습니다. ${name} 회전수를 올리는 중입니다.`
  if (status === 'APPLIED') return `${name} 회전수를 승인에 따라 올렸습니다.`
  if (status === 'DENIED') return `${name}${eulReul(name)} 바꾸지 않았습니다. 필요하면 현장에서 배기팬을 확인해 주세요.`
  if (status === 'FAILED') return `${name} 명령이 전달되지 않았거나 알림 종료로 취소됐습니다. 현장에서 배기팬을 확인해 주세요.`
  return `${name}${eunNeun(name)} 수동이라 자동으로 바꾸지 않았습니다. 필요하면 현장에서 배기팬을 확인해 주세요.`
})

/* ───────── 승인 · 거절 ───────── */
const busy = ref('') // 'approve' | 'deny' | ''
async function decide(approve) {
  busy.value = approve ? 'approve' : 'deny'
  try {
    const r = await decideFan(f.value.commandId, approve)
    override.value = { status: r.status, rpm: r.rpm ?? f.value.rpm }
    toast(approve ? `${f.value.fanName} 가동을 승인했습니다.` : `${f.value.fanName} 가동을 거절했습니다.`)
  } catch (error) {
    // 409: 다른 담당자가 먼저 처리했거나 알림이 끝나 취소됨 → 버튼을 숨기고 서버의 새 값(alarm.updated)을 기다림
    if (error.code === 'COMMAND_NOT_AWAITING') {
      handledElsewhere.value = true
      toast('이미 처리된 승인 요청입니다. 잠시 뒤 결과가 반영됩니다.', { type: 'err' })
    } else {
      toast(error.message, { type: 'err' })
    }
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div class="fan-auto" :class="[showRpm ? tone : 'manual', { fail: f.status === 'FAILED' }]">
    <!-- 돌고 있으면 아이콘이 돎 (자동 가동 · 승인해서 올림) -->
    <span class="ico" :class="{ spin: (isAuto || approved || manual) && f.status !== 'FAILED' }"><AppIcon name="fan" :size="20" /></span>
    <div class="txt">
      <b>{{ title }}</b>
      <small>{{ text }}</small>
    </div>
    <span v-if="showRpm" class="rpm mono">
      {{ rpmText(f.beforeRpm) }}<AppIcon name="cr" :size="12" /><em>{{ rpmText(targetRpm) }}</em><i>rpm</i>
    </span>
    <!-- 수동 승인 대기: 공장 담당자만 승인 · 거절 버튼 -->
    <span v-if="awaiting && canDecide && !handledElsewhere" class="acts">
      <button type="button" class="btn btn-primary" :disabled="!!busy" @click="decide(true)">{{ busy === 'approve' ? '승인 중' : '승인' }}</button>
      <button type="button" class="btn" :disabled="!!busy" @click="decide(false)">{{ busy === 'deny' ? '거절 중' : '거절' }}</button>
    </span>
    <span v-else-if="!isAuto" class="tag">수동</span>
    <!-- 가스 주의: 속도 직접 조절 20 ~ 60 Hz (공장 담당자만 — 승인 · 거절과 같은 권한) -->
    <FanSpeedControl v-if="adjustable && canDecide" :fan="f" class="speed" @sent="sentHz = $event" />
  </div>
</template>

<style scoped>
.fan-auto {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
  text-align: left;
  animation: softin 260ms both;
}

/* 주의: 주황 / 경보: 빨강 / 수동: 회색 */
.fan-auto.warn {
  border-color: #fdba74;
  background: var(--color-level-warn-soft);
}

.fan-auto.alarm {
  border-color: #fca5a5;
  background: var(--color-level-alarm-soft);
}

.fan-auto.manual {
  background: var(--color-surface-sub);
}

.ico {
  flex: none;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  color: var(--color-text-sub);
}

.warn .ico {
  color: var(--color-level-warn-text);
}

.alarm .ico {
  color: var(--color-level-alarm-text);
}

.ico.spin :deep(svg) {
  animation: fan-spin 0.9s linear infinite;
}

.txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.txt b {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

.warn .txt b {
  color: var(--color-level-warn-text);
}

.alarm .txt b,
.fail .txt b {
  color: var(--color-level-alarm-text);
}

.txt small {
  font-size: 12.5px;
  line-height: 18px;
  color: var(--color-text-sub);
}

.rpm {
  flex: none;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.rpm em {
  font-style: normal;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
}

.rpm i {
  font-style: normal;
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 승인 · 거절 버튼 · 속도 조절: 알림 창 폭이 좁아서 글 아래 한 줄로 따로 둠 */
.fan-auto:has(.acts),
.fan-auto:has(.speed) {
  flex-wrap: wrap;
}

.speed {
  flex: 1 0 100%;
}

.acts {
  flex: 1 0 100%;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

/* 공통 .btn 을 쓰되 한 줄 안에 들어가게 작게 */
.acts .btn {
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.tag {
  flex: none;
  padding: 2px 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: 10px;
  font-size: 12px;
  color: var(--color-text-sub);
}

@keyframes fan-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ico.spin :deep(svg) {
    animation: none;
  }
}
</style>
