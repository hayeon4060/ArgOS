<!--
  일별 알림 추이 (화면설계안 vStats() c1)
  - 주의·경보 누적 막대 + 7일 평균 선 (오늘이면 시간별, 63일 넘으면 주별 — 백엔드 기준)
  - 근무조가 '통합'이면 툴팁에 주간·야간 건수
  - 알림이 없으면 '조건에 맞는 알림이 없습니다'
-->
<script setup>
import { computed } from 'vue'
import { trendOption } from './historyCharts'
import BaseCard from '@/components/BaseCard.vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** GET /stats/trend (불러오기 전이면 null) */
  trend: { type: Object, default: null },
  sub: { type: String, default: '' },
  showShift: { type: Boolean, default: true },
})

const empty = computed(() => !!props.trend && props.trend.points.every((p) => p.warn + p.alarm === 0))
const option = computed(() => (props.trend && !empty.value ? trendOption(props.trend, props.showShift) : null))
</script>

<template>
  <BaseCard title="일별 알림 추이" :sub="sub">
    <template #actions>
      <span class="lg"><i class="warn" />주의</span>
      <span class="lg"><i class="alarm" />경보</span>
      <span v-if="trend?.unit === 'DAY'" class="lg"><i class="line" />7일 평균</span>
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

.lg .warn {
  background: var(--color-level-warn);
}

.lg .alarm {
  background: var(--color-level-alarm);
}

.lg .line {
  width: 16px;
  height: 2px;
  border-radius: 0;
  background: #334155;
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
