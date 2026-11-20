<!--
  유형별 건수 (화면설계안 vLogs() '유형별 건수' · .lty)
  - 8칸: 유형 이름 · 막대(가장 많은 칸 = 100%) · 건수. 막대는 0.4초에 늘어남
  - 칸을 누르면 표가 그 유형으로 걸러짐, 한 번 더 누르면 전체 유형 (pick(유형 key))
  - 건수는 서버 countsByType(코드별)를 칸별로 더함
-->
<script setup>
import { computed } from 'vue'
import { LOG_GROUPS } from './logMeta'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  /** GET /logs countsByType { LOGIN: 15, ... } */
  counts: { type: Object, default: () => ({}) },
  /** 고른 유형 key ('' 이면 전체) */
  group: { type: String, default: '' },
  sub: { type: String, default: '' },
})

defineEmits(['pick'])

const rows = computed(() => {
  const list = LOG_GROUPS.map((g) => ({ ...g, count: g.types.reduce((s, t) => s + (props.counts[t] ?? 0), 0) }))
  const max = Math.max(1, ...list.map((r) => r.count))
  return list.map((r) => ({ ...r, pct: (r.count / max) * 100 }))
})
</script>

<template>
  <BaseCard title="유형별 건수" :sub="sub">
    <div class="list">
      <button
        v-for="r in rows"
        :key="r.key"
        type="button"
        class="lty"
        :class="{ on: group === r.key, dim: group && group !== r.key }"
        :aria-pressed="group === r.key"
        @click="$emit('pick', group === r.key ? '' : r.key)"
      >
        <span class="nm">{{ r.label }}</span>
        <span class="bar"><i :style="{ width: `${r.pct}%`, background: r.color }" /></span>
        <b class="mono">{{ r.count }}</b>
      </button>
    </div>
  </BaseCard>
</template>

<style scoped>
.list {
  padding: 10px 12px 14px;
}

.lty {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition:
    background var(--m-hover),
    opacity 150ms;
}

.lty:hover {
  background: var(--color-surface-sub);
}

.lty.on {
  background: var(--color-primary-soft);
}

.lty.dim {
  opacity: 0.5;
}

.nm {
  width: 78px;
  flex: none;
  color: var(--color-text-sub);
}

.bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #eef2f6;
  overflow: hidden;
}

/* 막대가 0.4초에 늘어남 (설계안 growx, 화면설계서 '숫자 400ms') */
.bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  transform-origin: left;
  transition: width 400ms cubic-bezier(0.2, 0.8, 0.2, 1);
  animation: growx 400ms cubic-bezier(0.2, 0.8, 0.2, 1) both; /* 처음 그릴 때 0 에서 */
}

@keyframes growx {
  from {
    transform: scaleX(0);
  }
}

b {
  width: 36px;
  flex: none;
  font-weight: 600;
  text-align: right;
}

@media (prefers-reduced-motion: reduce) {
  .bar i {
    transition: none;
    animation: none;
  }
}
</style>
