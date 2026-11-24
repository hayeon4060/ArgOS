<!--
  전일 시간대별 알림 카드: 어제 시간대별 한 줄 (2026-11-22 재원님 요청: 왼쪽 '하루 00–24시' 글자 삭제) (GET /alarms/hourly?date=어제)
  - 2026-11-18: 제목 '시간대별 알림' → '전일 시간대별 알림', 부제(주간 · 야간) 삭제
  - 2026-11-20 재원님 요청: 주간(07~19시) · 야간(19~07시) 두 줄 → 구분 없이 하루 한 줄
    (두 칸 응답을 합치는 일은 api/dashboard.js 의 getHourlyAlarms 가 함)
  - 왼쪽에 '하루'·시간·알림/조치 건수, 오른쪽에 차트(HourlyChart)
  - 사업장 현황·공장 대시보드가 같이 씀
-->
<script setup>
import HourlyChart from './HourlyChart.vue'
import BaseCard from '@/components/BaseCard.vue'
import { CHART_COLORS } from '@/utils/chartColors'

defineProps({
  /** { alarmCount, actionCount, hours: [{ hour, warn, alarm, action }] } */
  hourly: { type: Object, required: true },
})

// 범례 (차트 색과 같게)
const LEGEND = [
  { label: '주의', color: CHART_COLORS.warn },
  { label: '경보', color: CHART_COLORS.alarm },
  { label: '조치', color: CHART_COLORS.action },
]
</script>

<template>
  <BaseCard title="전일 시간대별 알림">
    <template #actions>
      <span v-for="l in LEGEND" :key="l.label" class="legend"><i :style="{ background: l.color }" />{{ l.label }}</span>
    </template>

    <div class="body">
      <div class="label">
        <em>
          알림 <b class="mono">{{ hourly.alarmCount }}</b> ·
          조치 <b class="mono">{{ hourly.actionCount }}</b>
        </em>
      </div>
      <HourlyChart :hours="hourly.hours" />
    </div>
  </BaseCard>
</template>

<style scoped>
.legend {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

/* 카드 높이를 예전 두 줄(106px × 2 + 여백 12px)과 같게 */
.body {
  display: flex;
  align-items: flex-start;
  height: 224px;
  padding: 8px 12px 4px 8px;
}

.label {
  width: 84px;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 0 0 8px;
}

.label em {
  font-size: 11.5px;
  font-style: normal;
  line-height: 17px;
  color: var(--color-text-sub);
}

.label em b {
  font-size: 13px;
  color: var(--color-text);
}
</style>
