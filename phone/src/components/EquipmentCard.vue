<!--
  설비 한 장 (공정 현황 목록)
  - 왼쪽 띠 색 = 설비 단계, 오른쪽 위 단계 배지
  - 센서 지금 값: 정상은 회색, 주의·경보는 그 색 글자 + '주의 기준 21.6'
  - 배기팬이 있으면 켜짐·회전수
  - compact: 공정의 다른 설비 — 정상이면 센서 값을 한 줄로 줄임
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { LEVEL, LEVEL_META, sensorTypeOf } from '@/types/codes'

const props = defineProps({
  /** { name, level, fan, sensors: [{ id, tag, type, unit, value, level, warn, alarm }] } */
  equipment: { type: Object, required: true },
  compact: { type: Boolean, default: false },
})

const fmt = (s) => Number(s.value).toFixed(sensorTypeOf(s.type)?.dec ?? 0)
const short = () => props.compact && props.equipment.level === LEVEL.NORMAL
</script>

<template>
  <article class="eq" :class="`is-${LEVEL_META[equipment.level]?.key}`">
    <div class="head">
      <b>{{ equipment.name }}</b>
      <LevelBadge :level="equipment.level" />
    </div>

    <p v-if="short()" class="line mono">
      {{ equipment.sensors.map((s) => `${s.tag} ${fmt(s)}${s.unit}`).join(' · ') || '센서 없음' }}
    </p>

    <ul v-else class="sensors">
      <li v-for="s in equipment.sensors" :key="s.id" :class="`is-${LEVEL_META[s.level]?.key}`">
        <AppIcon :name="sensorTypeOf(s.type)?.icon ?? 'info'" :size="15" />
        <span class="tag mono">{{ s.tag }}</span>
        <b class="mono">{{ fmt(s) }}<small>{{ s.unit }}</small></b>
        <span v-if="s.level !== LEVEL.NORMAL" class="limit">
          {{ LEVEL_META[s.level].label }} 기준 <span class="mono">{{ s.level === LEVEL.WARN ? s.warn : s.alarm }}</span>
        </span>
      </li>
      <li v-if="!equipment.sensors.length" class="none">붙은 센서가 없습니다.</li>
    </ul>

    <p v-if="equipment.fan && !short()" class="fan">
      <AppIcon name="fan" :size="14" />배기팬 {{ equipment.fan.power === 'ON' ? '켜짐' : '꺼짐' }}
      <span v-if="equipment.fan.power === 'ON'" class="mono">{{ equipment.fan.rpm }} rpm</span>
    </p>
  </article>
</template>

<style scoped>
.eq {
  margin-bottom: 10px;
  padding: 14px 14px 12px;
  border: 1px solid var(--color-border-card);
  border-left: 5px solid var(--color-level-normal);
  border-radius: 12px;
  background: var(--color-surface);
}

.eq.is-warn {
  border-left-color: var(--color-level-warn);
}

.eq.is-alarm,
.eq.is-fire {
  border-left-color: var(--color-level-alarm);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 15px;
}

.line {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-sub);
}

.sensors {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sensors li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.tag {
  width: 52px;
  font-size: 12px;
}

.sensors b {
  font-size: 15px;
  color: var(--color-text);
}

.sensors small {
  margin-left: 2px;
  font-size: 11px;
  font-weight: 500;
}

.limit {
  margin-left: auto;
  font-size: 12px;
}

.sensors .is-warn,
.sensors .is-warn b {
  color: var(--color-level-warn-text);
}

.sensors .is-alarm,
.sensors .is-alarm b {
  color: var(--color-level-alarm-text);
}

.none {
  font-size: 12px;
}

.fan {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border);
  font-size: 12px;
  color: var(--color-text-sub);
}
</style>
