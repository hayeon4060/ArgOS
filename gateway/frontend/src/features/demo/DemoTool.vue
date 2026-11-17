<!--
  상황 바꾸기 도구 (화면설계안 '시연 도구'와 같은 역할) — 개발 모드 + 예시 데이터일 때만 보임
  - 왼쪽 아래 작은 버튼 → 펼치면 상황 버튼
    · 정상 / 주의 발생 / 경보 발생 / 화재 발생 / 처음 상황(주의 · 조치 중 2/5)
    · 담당자 앱 흉내: 항목 +1, 모두 완료 (체크리스트가 진행 중일 때)
  - 누르면 api/mock/scenario.js 의 상황이 바뀌고, 열린 화면이 모두 같이 바뀜
  - 실제 서버에 붙이면(VITE_USE_MOCK=false) 나오지 않음. 배포 빌드에도 나오지 않음 (MainLayout 에서 import.meta.env.DEV 로 거름)
-->
<script setup>
import { computed, ref } from 'vue'
import { activeChecklist, raiseWarn, resetScenario, scenario, setNormal, tickChecklist } from '@/api/mock/scenario'
import { raiseAlarm, raiseFire } from '@/api/mock/scenarioEmergency'
import AppIcon from '@/components/AppIcon.vue'
import { LEVEL_META } from '@/types/codes'

const open = ref(false)

/** 지금 상황 한 줄 설명 */
const summary = computed(() => {
  const label = LEVEL_META[scenario.state]?.label ?? scenario.state
  if (scenario.fire) return scenario.fire.status === 'ACTIVE' ? `화재 · 해제 전 (비밀번호 ${scenario.fire.failCount}/5)` : '화재 해제 · 설비 정지'
  if (scenario.incident) {
    if (scenario.incident.status === 'CLOSED') return '경보 종료 · 설비 정지'
    return scenario.shutdown ? '경보 · 셧다운 진행' : '경보 · 셧다운 전'
  }
  const a = scenario.alarm
  if (!a) return scenario.shut ? `${label} · 설비 정지` : label
  const c = activeChecklist()
  if (a.status === 'OPEN') return `${label} · 확인 전`
  if (a.status === 'ACKED') return `${label} · 확인함, 체크리스트 전송 전`
  if (c) return `${label} · 체크리스트 ${c.doneTimes.length}/${c.items.length}${c.status === 'REPORTED' ? ' 완료 보고' : ''}`
  return `${label} · 조치 완료`
})

const checklist = computed(() => activeChecklist())
</script>

<template>
  <div class="demo">
    <div v-if="open" class="panel pop-in">
      <div class="head">
        <b>상황 바꾸기</b>
        <span class="now">{{ summary }}</span>
      </div>

      <div class="group">
        <span class="label">상황</span>
        <button type="button" :class="{ on: scenario.state === 'NORMAL' && !scenario.alarm }" @click="setNormal">정상</button>
        <button type="button" :class="{ on: scenario.alarm?.status === 'OPEN' }" @click="raiseWarn">주의 발생</button>
        <button type="button" :class="{ on: scenario.state === 'ALARM' }" @click="raiseAlarm">경보 발생</button>
        <button type="button" :class="{ on: scenario.state === 'FIRE' }" @click="raiseFire">화재 발생</button>
        <button type="button" @click="resetScenario">처음 상황</button>
      </div>

      <div class="group">
        <span class="label">담당자 앱</span>
        <button type="button" :disabled="!checklist || checklist.status === 'REPORTED'" @click="tickChecklist(1)">항목 +1</button>
        <button type="button" :disabled="!checklist || checklist.status === 'REPORTED'" @click="tickChecklist(99)">모두 완료</button>
      </div>

      <p class="hint">
        처음 상황 = 주의 · 김도현 2/5 진행 중. 전송하면 5.2초마다 한 항목씩 자동 진행. 화재 해제 비밀번호 = 로그인 비밀번호<br />
        받는 담당자가 담당자 앱(/phone/)에 로그인해 있으면 자동 진행 없이 앱에서 직접 체크
      </p>
    </div>

    <button type="button" class="toggle" :aria-expanded="open" @click="open = !open">
      <AppIcon name="slider" :size="16" />상황 바꾸기
    </button>
  </div>
</template>

<style scoped>
/* 맨 위 (창·알림 띠보다 위): 창이 열린 채로도 상황을 바꿀 수 있게 */
.demo {
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 18px;
  background: #475569;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.25);
  cursor: pointer;
}

.panel {
  width: 320px;
  padding: 14px;
  border-radius: var(--radius-md);
  background: #fff;
  border: 1px solid var(--color-border);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.22);
  font-size: 13px;
}

.head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 10px;
}

.now {
  color: var(--color-text-sub);
  font-size: 12px;
}

.group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.label {
  width: 60px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.group button {
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12.5px;
  cursor: pointer;
}

.group button.on {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 700;
}

.group button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.hint {
  margin-top: 10px;
  font-size: 11.5px;
  line-height: 17px;
  color: var(--color-text-weak);
}
</style>
