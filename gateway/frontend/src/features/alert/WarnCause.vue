<!--
  주의 상세 '발생 원인' 상자 (설계안 mWarnDetail() 왼쪽 위)
  - 원인 문장, '평소 18.0 A 대비 124% · 16:38:20부터 지속'
  - 기준 띠: 0 ~ 눈금 끝, 초록(정상)·주황(주의)·빨강(경보) 구간 + 넘었을 때 값 표시
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** GET /alarms/{id}/detail 응답 */
  detail: { type: Object, required: true },
})

const sensor = computed(() => props.detail.sensors.find((s) => s.tag === props.detail.rule.sensorTag))
const max = computed(() => props.detail.rule.max ?? props.detail.rule.alarm * 1.2)
const pct = (v) => `${Math.min(100, (v / max.value) * 100)}%`
const percentOfUsual = computed(() => Math.round((sensor.value.value / props.detail.usual) * 100))
const since = computed(() => props.detail.raisedAt.slice(11, 19))

// 띠 색 구간 (정상 → 주의 → 경보)
const bandStyle = computed(() => {
  const w = (props.detail.rule.warn / max.value) * 100
  const a = (props.detail.rule.alarm / max.value) * 100
  return { background: `linear-gradient(90deg, #86efac 0 ${w}%, #fdba74 ${w}% ${a}%, #fca5a5 ${a}% 100%)` }
})
</script>

<template>
  <div class="m-box cause">
    <div class="sentence">{{ detail.cause }}</div>
    <div class="sub">평소 {{ detail.usual.toFixed(1) }} {{ sensor.unit }} 대비 {{ percentOfUsual }}% · {{ since }}부터 지속</div>
    <div class="band" :style="bandStyle">
      <i :style="{ left: pct(sensor.value) }" />
      <span class="mono" :style="{ left: 0, transform: 'none' }">0</span>
      <span class="mono" :style="{ left: pct(detail.rule.warn) }">주의 {{ detail.rule.warn }}</span>
      <span class="mono" :style="{ left: pct(detail.rule.alarm) }">경보 {{ detail.rule.alarm.toFixed(1) }}</span>
      <span class="mono now" :style="{ left: pct(sensor.value) }">현재 {{ sensor.value }} {{ sensor.unit }}</span>
    </div>
  </div>
</template>

<style scoped>
.cause {
  padding: 16px 18px;
}

.sentence {
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
}

.sub {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

/* 기준 띠 (설계안 .lin) */
.band {
  position: relative;
  height: 10px;
  margin: 26px 4px 26px;
  border-radius: 5px;
}

.band i {
  position: absolute;
  top: -5px;
  width: 4px;
  height: 20px;
  margin-left: -2px;
  border-radius: 2px;
  background: var(--color-text);
  /* 값이 바뀌면 400ms 로 움직임 (화면설계서 C2) */
  transition: left var(--m-value);
}

.band span {
  position: absolute;
  top: 18px;
  transform: translateX(-50%);
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}

.band span.now {
  top: -22px;
  font-weight: 600;
  color: var(--color-text);
}
</style>
