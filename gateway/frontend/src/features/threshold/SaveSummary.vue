<!--
  저장 확인 창 내용 (화면설계안 vThreshold() 저장 confirmBox)
  - 바꾼 값마다 '전류 주의  ̶2̶2̶.̶5̶ → 21.6 A'
  - 어디에 언제 적용되는지: 이 설비 / 건조로 3대, 지금 계절이면 바로, 다른 계절이면 그 계절 시작일부터
  - 변경 사유 한 줄 (명세서 PUT 의 reason, 필수 — 비어 있으면 확인 창의 저장 단추가 꺼지고 안내 문구)
-->
<script setup>
import { fmt, label, SEASON_META } from './thresholdForm'
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  /** [{ sensor, field, before, after }] */
  changes: { type: Array, required: true },
  scope: { type: String, default: 'one' },
  season: { type: String, required: true },
  activeSeason: { type: String, required: true },
  /** 그 계절 시작일 'YYYY-MM-DD' */
  validFrom: { type: String, default: '' },
})

const reason = defineModel('reason', { type: String, default: '' })
</script>

<template>
  <div class="rows">
    <div v-for="c in changes" :key="`${c.sensor.sensorId}-${c.field}`" class="row">
      <b>{{ label(c.sensor) }} {{ c.field === 'warn' ? '주의' : '경보' }}</b>
      <s class="mono">{{ fmt(c.before) }}</s><AppIcon name="arrow" :size="14" class="ar" />
      <b class="mono new">{{ fmt(c.after) }} {{ c.sensor.unit }}</b>
    </div>
  </div>
  <p class="where">
    {{ scope === 'ovens' ? '건조로 3대에 모두' : '이 설비에' }}
    {{ season === activeSeason ? '바로 적용됩니다.' : `${SEASON_META[season].label} 기준으로 저장되고 ${validFrom.slice(5).replace('-', '월 ')}일부터 적용됩니다.` }}
  </p>
  <label class="reason">
    <span>변경 사유 <em>필수</em></span>
    <input v-model="reason" type="text" maxlength="80" placeholder="예: 베어링 교체 전까지 주의 기준을 낮춤" />
  </label>
  <p v-if="!reason.trim()" class="need">변경 사유를 적어야 저장할 수 있습니다.</p>
</template>

<style scoped>
.rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row > b:first-child {
  width: 92px;
  font-weight: 600;
  color: var(--color-text);
}

s {
  color: var(--color-text-weak);
}

.ar {
  color: var(--color-text-weak);
}

.new {
  color: var(--color-primary-hover);
}

.where {
  margin-top: 8px;
}

.reason {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  font-size: 12.5px;
}

.reason em {
  margin-left: 4px;
  font-style: normal;
  font-weight: 600;
  color: var(--color-level-alarm);
}

.need {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.reason input {
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 13.5px;
  color: var(--color-text);
}
</style>
