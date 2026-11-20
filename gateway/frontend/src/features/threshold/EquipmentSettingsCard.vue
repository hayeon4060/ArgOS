<!--
  설비 기준값 관리 창의 '설비 설정' 줄 (2026-11-19 재원님 요청: 공장 관리 설비 정보 카드에 있던 스위치 2개를 이 창으로 옮김)
  - 사용 상태 (DB equipment.is_active) / 배기팬 자동 제어 (DB equipment.fan_auto, 배기팬 있는 설비만)
  - 스위치를 누르면 toggle(필드, 새 값) → 창이 PATCH /equipment/{id}/settings 로 바로 저장 (기준값 '저장'과 따로)
  - 저장하는 동안 그 스위치는 누를 수 없음 (busy)
-->
<script setup>
import { FAN_AUTO_PERCENT } from '@/types/codes'

defineProps({
  /** 사용 상태 */
  active: { type: Boolean, required: true },
  /** 배기팬 자동(true)/수동(false), 배기팬이 없으면 null → 스위치 안 보임 */
  fanAuto: { type: Boolean, default: null },
  /** 저장 중인 필드 ('active' | 'fanAuto' | '') */
  busy: { type: String, default: '' },
})

const emit = defineEmits(['toggle'])

// 최종 ERD: 알림이 끝나면 서버가 평소 회전수로 되돌림 (equipment_command.source = RECOVERY)
const FAN_TEXT = `가스(CO) 주의 ${FAN_AUTO_PERCENT.WARN}% · 경보 ${FAN_AUTO_PERCENT.ALARM}%로 올리고, 알림이 끝나면 평소대로 되돌립니다.`
</script>

<template>
  <section class="set-card base-card">
    <span class="title">설비 설정</span>

    <!-- 사용 상태 -->
    <div class="item">
      <div class="txt">
        <span>사용 상태 <b :class="{ ok: active }">{{ active ? '사용 중' : '사용 안 함' }}</b></span>
        <small>사용 안 함이면 배치도에서 흐리게 보입니다.</small>
      </div>
      <button type="button" class="tg" :class="{ on: active }" role="switch" :aria-checked="active" aria-label="사용 상태" :disabled="busy === 'active'" @click="emit('toggle', 'active', !active)" />
    </div>

    <!-- 배기팬 자동 제어 (배기팬 있는 설비만) -->
    <div v-if="fanAuto !== null" class="item">
      <div class="txt">
        <span>배기팬 자동 제어 <b :class="{ ok: fanAuto }">{{ fanAuto ? '자동' : '수동' }}</b></span>
        <small>{{ fanAuto ? FAN_TEXT : '가스 알림이 오면 알림 창에서 공장 담당자가 승인해야 배기팬을 올립니다.' }}</small>
      </div>
      <button type="button" class="tg" :class="{ on: fanAuto }" role="switch" :aria-checked="fanAuto" aria-label="배기팬 자동 제어" :disabled="busy === 'fanAuto'" @click="emit('toggle', 'fanAuto', !fanAuto)" />
    </div>
  </section>
</template>

<style scoped>
/* 한 줄: 제목 | 사용 상태 | 배기팬 자동 제어 */
.set-card {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 14px 22px;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  animation: softin 240ms both;
}

.title {
  flex: none;
  font-size: 15px;
  font-weight: 700;
}

.item {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding-left: 24px;
  border-left: 1px solid var(--color-border);
}

.txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.txt span {
  font-size: 14px;
  color: var(--color-text-sub);
}

.txt b {
  margin-left: 6px;
  color: var(--color-text-weak);
}

.txt b.ok {
  color: var(--color-level-normal-text);
}

.txt small {
  font-size: 12.5px;
  color: var(--color-text-weak);
}

/* 스위치 (공장 관리에 있던 것과 같은 모양) */
.tg {
  position: relative;
  flex: none;
  width: 40px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 11px;
  background: #cbd5e1;
  cursor: pointer;
  transition: background 200ms;
}

.tg:disabled {
  opacity: 0.6;
  cursor: wait;
}

.tg::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: transform 200ms;
}

.tg.on {
  background: var(--color-level-normal);
}

.tg.on::after {
  transform: translateX(18px);
}
</style>
