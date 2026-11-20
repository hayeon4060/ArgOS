<!--
  전체 공장 상태 카드 안의 공장 하나 (2026-11-18 시안 B v3 기준)
  - 이름 + 상태 배지, 전류·온도·CO 칸 3개
  - 칸에는 '지금 알림이 난 센서 수'만 표시 (전체 센서 수는 보여 주지 않음)
    · 알림 없음: 초록 체크
    · 주의만: 주황 숫자 + '주의' / 경보만: 빨간 숫자 + '경보'
    · 둘 다: '경보 수 | 주의 수' 를 나눠 적고 아래에 '경보 · 주의'
  - 카드 테두리·배경은 공장 단계(가장 높은 단계) 색
  - 누르면 select 이벤트 (들어갈 수 있는 공장이면 이동, 아니면 지도 말풍선)
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { keyOf } from '@/features/site/stages'
import { LEVEL, SENSOR_TYPES } from '@/types/codes'
import { blinkClass } from '@/utils/blink'

const props = defineProps({
  /** 사업장 현황의 공장 하나 (levelsByType: { CURRENT: { NORMAL, WARN, ALARM }, TEMP, CO }) */
  factory: { type: Object, required: true },
})

const emit = defineEmits(['select'])

/** 칸 하나의 표시 값: 경보 수·주의 수와 칸 색 이름 */
function cellOf(counts = {}) {
  const warn = counts[LEVEL.WARN] ?? 0
  const alarm = (counts[LEVEL.ALARM] ?? 0) + (counts[LEVEL.FIRE] ?? 0) // 화재는 경보 칸에 셈
  const kind = alarm ? 'alarm' : warn ? 'warn' : 'none'
  return { warn, alarm, kind }
}

// 전류·온도·CO 칸 3개 (이름 + 표시 값)
const cells = computed(() =>
  SENSOR_TYPES.map((t) => ({ type: t.type, label: t.label, ...cellOf(props.factory.levelsByType?.[t.type]) })),
)
</script>

<template>
  <!-- 주의·경보·화재 공장은 카드 바깥으로 빛번짐 점멸 (blink.css blink-glow) -->
  <button
    type="button"
    class="factory-item"
    :class="[`is-${keyOf(factory.level)}`, blinkClass(factory.level) && ['blink-glow', blinkClass(factory.level)]]"
    @click="emit('select', factory)"
  >
    <span class="head">
      {{ factory.name }}
      <LevelBadge :level="factory.level" />
    </span>

    <span class="cells">
      <span v-for="c in cells" :key="c.type" class="cell" :class="`is-${c.kind}`">
        <span class="label">{{ c.label }}</span>

        <!-- 알림 없음: 초록 체크만 -->
        <AppIcon v-if="c.kind === 'none'" name="check" :size="36" class="ok-icon" />

        <!-- 경보·주의 둘 다: 경보 수 | 주의 수 -->
        <template v-else-if="c.alarm && c.warn">
          <span class="num two mono"><em>{{ c.alarm }}</em><i class="sep" /><em class="w">{{ c.warn }}</em></span>
          <span class="cap small">경보 · <span class="w">주의</span></span>
        </template>

        <!-- 한 가지만 -->
        <template v-else>
          <span class="num mono">{{ c.alarm || c.warn }}</span>
          <span class="cap">{{ c.alarm ? '경보' : '주의' }}</span>
        </template>
      </span>
    </span>
  </button>
</template>

<style scoped>
.factory-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 14px 11px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.factory-item:hover {
  border-color: #93c5fd;
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.12);
}

/* 주의·경보 공장은 테두리·배경을 상태 색으로 */
.factory-item.is-warn {
  border-color: var(--color-level-warn);
  background: rgba(255, 237, 213, 0.4);
}

.factory-item.is-alarm,
.factory-item.is-fire {
  border-color: var(--color-level-alarm);
  background: rgba(254, 226, 226, 0.4);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
}

.cells {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
  margin-top: 12px;
}

/* 칸: 이름은 위에 고정, 체크·숫자는 남은 공간 가운데 */
.cell {
  --c: var(--color-text-sub);
  min-height: 112px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 11px 0 10px;
  border: 1px solid #eef2f6;
  border-radius: 6px;
  background: #fff;
}

.cell.is-warn {
  --c: var(--color-level-warn);
  border: 1.5px solid var(--color-level-warn);
}

.cell.is-alarm {
  --c: var(--color-level-alarm);
  border: 1.5px solid var(--color-level-alarm);
}

.label {
  font-size: 14px;
  font-weight: 600;
  color: var(--c);
}

.cell.is-none .label {
  color: #64748b;
}

.ok-icon {
  margin: auto 0;
  color: var(--color-level-normal);
}

.num {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 48px;
  margin-top: auto;
  font-size: 44px;
  font-weight: 600;
  line-height: 48px;
  color: var(--c);
}

.num.two {
  font-size: 30px;
}

.num em {
  font-style: normal;
}

.num .sep {
  width: 1px;
  height: 24px;
  background: #f3c1b0;
}

.cap {
  margin-bottom: auto;
  font-size: 13px;
  font-weight: 600;
  color: var(--c);
}

.cap.small {
  font-size: 11.5px;
  letter-spacing: -0.3px;
}

/* 경보·주의가 같이 있을 때 주의 쪽 숫자·글자는 주황 */
.w {
  color: var(--color-level-warn);
}
</style>
