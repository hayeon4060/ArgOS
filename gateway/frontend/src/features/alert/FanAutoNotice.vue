<!--
  가스(CO) 알림 창의 '배기팬 자동 제어' 한 줄 (2026-11-19 재원님 요청)
  - 주의 알림 창 · 주의 상세 창 · 경보 대응 창에 같이 씀
  - 값은 알림 응답의 fanAuto 그대로 보여 주기만 함 (실제로 팬을 돌리는 건 서버, backend-notes 20장)
    { fanName, mode: 'AUTO'|'MANUAL', percent, rpm, beforeRpm, status: 'APPLIED'|'SENT'|'FAILED'|'SKIPPED' }
  - 자동(AUTO): '배기팬 자동 가동 75%' + '1,200 → 1,350 rpm' (팬 아이콘이 돎)
    수동(MANUAL): 안내만 ('수동 모드라 바꾸지 않았습니다')
  - 자동/수동은 '설비 기준값 관리' 창 › 설비 설정의 '배기팬 자동 제어' 스위치로 정함 (2026-11-19 설비 정보 카드에서 옮김)
  - 대시보드에서 팬을 손으로 조절하는 창은 2026-11-19 삭제 → 수동·실패 때는 현장 확인 안내
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** 알림 응답의 fanAuto (가스 알림이 아니거나 배기팬이 없으면 부모가 그리지 않음) */
  fan: { type: Object, required: true },
  /** 색: 'warn' 주황 / 'alarm' 빨강 */
  tone: { type: String, default: 'warn' },
})

/** 받침에 맞는 '은/는' ('배기팬은', '팬모터는') */
const eunNeun = (word) => {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  return code >= 0 && code <= 11171 && code % 28 ? '은' : '는'
}

const rpmText = (v) => (v == null ? '–' : Number(v).toLocaleString())
const isAuto = computed(() => props.fan.mode === 'AUTO')

/** 굵은 제목 줄 */
const title = computed(() => {
  if (!isAuto.value) return '배기팬 수동 모드'
  if (props.fan.status === 'FAILED') return `배기팬 ${props.fan.percent}% 자동 변경 실패`
  if (props.fan.status === 'SENT') return `배기팬 ${props.fan.percent}%로 올리는 중`
  return `배기팬 자동 가동 ${props.fan.percent}%`
})

/** 아래 설명 줄 */
const text = computed(() => {
  const name = props.fan.fanName
  if (!isAuto.value) return `${name}${eunNeun(name)} 수동이라 자동으로 바꾸지 않았습니다. 필요하면 현장에서 배기팬을 확인해 주세요.`
  if (props.fan.status === 'FAILED') return `${name} 명령이 전달되지 않았습니다. 현장에서 배기팬을 확인해 주세요.`
  return `${name} 회전수를 자동으로 올렸습니다.`
})
</script>

<template>
  <div class="fan-auto" :class="[isAuto ? tone : 'manual', { fail: fan.status === 'FAILED' }]">
    <!-- 자동으로 돌고 있으면 아이콘이 돎 -->
    <span class="ico" :class="{ spin: isAuto && fan.status !== 'FAILED' }"><AppIcon name="fan" :size="20" /></span>
    <div class="txt">
      <b>{{ title }}</b>
      <small>{{ text }}</small>
    </div>
    <span v-if="isAuto" class="rpm mono">
      {{ rpmText(fan.beforeRpm) }}<AppIcon name="cr" :size="12" /><em>{{ rpmText(fan.rpm) }}</em><i>rpm</i>
    </span>
    <span v-else class="tag">수동</span>
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
