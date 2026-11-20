<!--
  공정별 알림 (화면설계안 vStats() c2)
  - 공정마다 센서 종류별(전류·온도·CO) 누적 가로 막대, 오른쪽 합계, 많은 순
-->
<script setup>
import { computed } from 'vue'
import { processOption } from './historyCharts'
import { SENSOR_TYPES } from './historyFilter'
import { SENSOR_CHART_COLOR } from '@/utils/chartColors'
import BaseCard from '@/components/BaseCard.vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** GET /stats/by-process (불러오기 전이면 null) */
  rows: { type: Array, default: null },
  sub: { type: String, default: '' },
})

const empty = computed(() => !!props.rows && props.rows.length === 0)
const option = computed(() => (props.rows?.length ? processOption(props.rows) : null))
</script>

<template>
  <BaseCard title="공정별 알림" :sub="sub">
    <template #actions>
      <span v-for="t in SENSOR_TYPES" :key="t.value" class="lg"><i :style="{ background: SENSOR_CHART_COLOR[t.value] }" />{{ t.label }}</span>
    </template>
    <VChart v-if="option" class="chart" :option="option" autoresize />
    <div v-else-if="empty" class="chart noval"><AppIcon name="bell" :size="24" /><b>조건에 맞는 알림이 없습니다</b>기간이나 조건을 바꿔 보세요.</div>
    <div v-else class="chart noval">불러오는 중</div>
  </BaseCard>
</template>

<style scoped>
/* 카드 높이는 페이지가 정함(화면 높이를 두 그래프가 나눠 가짐) → 그래프가 제목 아래 남는 높이를 채움 */
.base-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chart {
  flex: 1;
  min-height: 180px; /* 이보다 낮으면 막대·눈금이 겹침 */
}

.lg {
  display: flex;
  align-items: center;
  gap: 6px;
}

.lg i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.noval {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.noval b {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-sub);
}
</style>
