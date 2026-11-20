<!--
  시간대별 이용 (화면설계안 vLogs() '시간대별 이용')
  - 0~23시 막대 그래프: 낮(7~18시) 파랑, 그 밖 회색. 기간 합계
  - 아래 '많이 쓴 사용자' 4명 (순위 · 이름 첫 글자 · 이름 · 건수)
  - byHour 는 명세서 그대로, topUsers 는 명세서에 없음 (backend-notes 16장)
-->
<script setup>
import { computed } from 'vue'
import { hourColor } from './logMeta'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import { CHART_COLORS } from '@/utils/chartColors'
import { chartMotion } from '@/utils/echarts'

const props = defineProps({
  /** GET /logs byHour [{ hour, count }] (없는 시간은 0) */
  byHour: { type: Array, default: () => [] },
  /** [{ loginId, name, count }] */
  topUsers: { type: Array, default: () => [] },
})

const AXIS = {
  axisLine: { lineStyle: { color: CHART_COLORS.grid } },
  axisTick: { show: false },
  axisLabel: { color: CHART_COLORS.axis, fontSize: 11, fontFamily: 'Fira Code, monospace' },
  splitLine: { lineStyle: { color: CHART_COLORS.grid } },
}

const option = computed(() => {
  const counts = Array.from({ length: 24 }, (_, h) => props.byHour.find((x) => x.hour === h)?.count ?? 0)
  return {
    ...chartMotion(),
    grid: { left: 36, right: 16, top: 14, bottom: 26 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (it) => `${it[0].dataIndex}시 · ${it[0].value}건` },
    xAxis: { type: 'category', data: counts.map((_, h) => h), ...AXIS, axisLabel: { ...AXIS.axisLabel, interval: 3, formatter: '{value}시' } },
    yAxis: { type: 'value', ...AXIS, axisLine: { show: false }, splitNumber: 3, minInterval: 1 },
    series: [
      {
        type: 'bar',
        barWidth: 10,
        data: counts.map((v, h) => ({ value: v, itemStyle: { color: hourColor(h), borderRadius: [2, 2, 0, 0] } })),
      },
    ],
  }
})
</script>

<template>
  <BaseCard title="시간대별 이용" sub="기간 합계">
    <VChart class="chart" :option="option" autoresize />
    <div class="top">
      <div class="sect"><AppIcon name="users" :size="15" />많이 쓴 사용자</div>
      <div v-for="(u, i) in topUsers" :key="u.loginId" class="ltu">
        <span class="mono rank">{{ i + 1 }}</span>
        <span class="av">{{ u.name[0] }}</span>
        <b>{{ u.name }}</b>
        <span class="mono id">{{ u.loginId }}</span>
        <span class="mono cnt">{{ u.count }}건</span>
      </div>
      <p v-if="!topUsers.length" class="none">기록이 없습니다.</p>
    </div>
  </BaseCard>
</template>

<style scoped>
.chart {
  height: 196px;
  margin-top: 4px;
}

.top {
  padding: 4px 20px 16px;
}

.sect {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.ltu {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid #f1f5f9;
  font-size: 13.5px;
}

.rank {
  width: 14px;
  color: var(--color-text-weak);
}

.av {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eef2f6;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-sub);
}

b {
  font-weight: 600;
}

.id {
  font-size: 12px;
  color: var(--color-text-weak);
}

.cnt {
  margin-left: auto;
  color: var(--color-text-sub);
}

.none {
  margin: 0;
  padding: 10px 0;
  font-size: 13px;
  color: var(--color-text-weak);
}
</style>
