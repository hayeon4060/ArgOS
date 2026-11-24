<!--
  경보 창 '센서 데이터' (화면설계안 mAlarm() 가운데: 센서마다 작은 게이지 + 이름 · 단계 · 기준)
  - 기준을 넘은 센서는 상자 테두리가 단계 색
  - 값이 바뀌면(셧다운 단계마다 내려감) 게이지가 400ms 로 움직임
  - 설계안의 최근 추이 작은 선(스파크라인)은 아직 안 넣음
  - 받는 sensors 는 AlarmResponseModal 이 고른 것 (alarmSensors.js): 경보가 난 관제 단위의 전류 · 온도 · CO 만,
    level 은 서버 단계와 지금 값 단계 중 높은 쪽 → 배지 · 게이지 색 · 테두리가 모두 이 level 을 씀
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { gaugeOption } from '@/features/equipment/gaugeOption'
import { LEVEL, LEVEL_META, sensorTypeOf } from '@/types/codes'

defineProps({
  /** GET /alarms/{id}/response 의 sensors 중 보여 줄 것 (alarmSensors.js pickAlarmSensors) */
  sensors: { type: Array, required: true },
})

// 눈금 끝: 경보 기준의 1.2배 (공정 배치도 작은 고리와 같음)
const option = (s) =>
  gaugeOption({
    value: s.value,
    max: s.alarm * 1.2,
    warn: s.warn,
    alarm: s.alarm,
    unit: s.unit,
    level: s.level,
    dec: sensorTypeOf(s.type)?.dec ?? 0,
    fontSize: 22,
    width: 9,
  })
</script>

<template>
  <div class="list">
    <div v-for="s in sensors" :key="s.tag" class="m-box card" :style="s.level !== LEVEL.NORMAL ? { borderColor: LEVEL_META[s.level]?.color } : {}">
      <VChart class="gauge" :option="option(s)" autoresize />
      <div class="info">
        <div class="name">
          <AppIcon :name="sensorTypeOf(s.type)?.icon" :size="15" />{{ sensorTypeOf(s.type)?.label }}
          <span class="mono tag">{{ s.tag }}</span>
          <LevelBadge :level="s.level" class="badge" />
        </div>
        <div class="th">주의 {{ s.warn }} · 경보 {{ s.alarm }} {{ s.unit }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card {
  height: 142px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px 0 6px;
  transition: border-color 300ms;
}

.gauge {
  width: 140px;
  height: 140px;
  flex: none;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
}

.tag {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.badge {
  margin-left: auto;
}

.th {
  margin-top: 10px;
  font-size: 12px;
  color: #64748b;
}
</style>
