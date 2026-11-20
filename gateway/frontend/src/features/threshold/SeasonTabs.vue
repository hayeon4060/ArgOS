<!--
  계절별 기준값 고르기 (화면설계안 vThreshold() seat())
  - '봄 · 가을 3~5월, 9~10월' / '여름 6~8월' / '겨울 11~2월 [지금 적용 중]'
  - 오른쪽 안내: 계절이 바뀌는 날 0시에 자동으로 바뀝니다
  - 고르면 select(계절) — 바꾼 값이 있을 때의 확인은 페이지가 함
-->
<script setup>
import { SEASON_META } from './thresholdForm'
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  season: { type: String, required: true },
  activeSeason: { type: String, required: true },
})

defineEmits(['select'])

const ORDER = ['SPRING_FALL', 'SUMMER', 'WINTER']
</script>

<template>
  <div class="seat" role="tablist" aria-label="계절별 기준값">
    <span class="lb"><AppIcon name="cal" :size="16" />계절별 기준값</span>
    <button
      v-for="s in ORDER"
      :key="s"
      type="button"
      role="tab"
      class="tab"
      :class="{ on: s === season }"
      :aria-selected="s === season"
      @click="$emit('select', s)"
    >
      <b>{{ SEASON_META[s].label }}</b>
      <small class="mono">{{ SEASON_META[s].months }}</small>
      <span v-if="s === activeSeason" class="m-chip ok">지금 적용 중</span>
    </button>
    <span class="nt"><AppIcon name="info" :size="14" />계절이 바뀌는 날 0시에 자동으로 바뀝니다</span>
  </div>
</template>

<style scoped>
.seat {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.lb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-right: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.tab {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 150ms,
    background 150ms,
    box-shadow 150ms;
}

.tab:hover {
  border-color: #93c5fd;
}

.tab.on {
  padding: 0 13px;
  border: 2px solid var(--color-primary);
  background: var(--color-primary-soft);
}

.tab b {
  font-size: 14px;
}

.tab small {
  font-size: 12px;
  color: var(--color-text-weak);
}

.nt {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--color-text-weak);
  white-space: nowrap;
}
</style>
