<!--
  전체 사업장 센서 단계 카드: 제목줄(전체 합계) + 사업장별 도넛
  - 도넛 카드를 누르면 select 이벤트로 사업장 ID 를 알림
-->
<script setup>
import { computed } from 'vue'
import SensorDonut from './SensorDonut.vue'
import StageCounts from './StageCounts.vue'
import { sumLevels, totalOf } from './siteStats'
import { keyOf } from './stages'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  sites: { type: Array, required: true },
})

const emit = defineEmits(['select'])

// 전체 사업장 합계
const allCounts = computed(() => sumLevels(props.sites.map((s) => s.sensorLevels)))
</script>

<template>
  <BaseCard title="전체 사업장 센서 단계" :sub="`${sites.length}곳 · 센서 ${totalOf(allCounts)}개`">
    <template #actions>
      <StageCounts :counts="allCounts" />
    </template>

    <div class="donuts">
      <button
        v-for="site in sites"
        :key="site.id"
        type="button"
        class="donut-card"
        :class="`is-${keyOf(site.level)}`"
        @click="emit('select', site.id)"
      >
        <SensorDonut :counts="site.sensorLevels" label="센서" />
        <b>{{ site.name }}</b>
        <span class="mono code">{{ site.code }}</span>
        <StageCounts :counts="site.sensorLevels" vertical class="list" />
      </button>
    </div>
  </BaseCard>
</template>

<style scoped>
.donuts {
  display: flex;
  gap: 12px;
  padding: 16px 20px 18px;
}

.donut-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 16px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}

.donut-card:hover {
  border-color: #93c5fd;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

/* 주의·경보 사업장은 카드 테두리·배경을 상태 색으로 */
.donut-card.is-warn {
  border-color: var(--color-level-warn);
  background: #fff7ed;
}

.donut-card.is-alarm,
.donut-card.is-fire {
  border-color: var(--color-level-alarm);
  background: #fef2f2;
}

.donut-card > b {
  margin-top: 10px;
  font-size: 15px;
}

.code {
  font-size: 11.5px;
  color: var(--color-text-weak);
}

.list {
  align-self: stretch;
  margin-top: 10px;
}
</style>
