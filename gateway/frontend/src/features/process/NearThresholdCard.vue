<!--
  기준값 근접 센서 카드 (공정 3D 설비 배치도 오른쪽 위, 2026-11-18 시안 v10 기준)
  - 주의 기준에 가까운 순서로 5개. 값이 바뀌면 순위가 바뀌고 줄이 미끄러지듯 자리를 옮김(TransitionGroup)
  - 줄마다: 순위 · 센서 + 설비 이름 · 센서 종류 칩 · 막대(지금 값, 주의·경보 기준선) · 값과 '주의 기준 몇 %'
  - 막대·값 색 = 단계 색 (정상 초록 / 주의 주황 / 경보 빨강)
  - 주의·경보 줄은 바탕이 단계 색으로 천천히 반짝임 (2026-11-18 재원님 요청)
  - 줄을 누르면 select 이벤트 (페이지가 그 설비 상세로 이동)
-->
<script setup>
import BaseCard from '@/components/BaseCard.vue'
import { LEVEL, sensorTypeOf } from '@/types/codes'
import { blinkClass } from '@/utils/blink'

defineProps({
  /** features/process/nearThreshold.js nearThresholdRows() 결과 */
  rows: { type: Array, required: true },
})

const emit = defineEmits(['select'])

// 단계 → 색 이름 (CSS 클래스). 화재는 경보와 같은 빨강
const levelClass = (level) => ({ [LEVEL.WARN]: 'warn', [LEVEL.ALARM]: 'alarm', [LEVEL.FIRE]: 'alarm' })[level] ?? 'ok'

// 막대 눈금: 0 ~ 경보 기준의 110% (경보를 넘어도 막대 끝에 여유가 있게)
const at = (row, x) => `${Math.min(100, (x / (row.alarm * 1.1)) * 100).toFixed(1)}%`

// 값 자릿수: 센서 종류 정보(types/codes.js)의 dec
const fmt = (row) => row.value.toFixed(sensorTypeOf(row.type)?.dec ?? 1)
</script>

<template>
  <BaseCard title="기준값 근접 센서">
    <template #actions>
      <span class="legend"><i class="ok" />정상</span>
      <span class="legend"><i class="warn" />주의</span>
      <span class="legend"><i class="alarm" />경보</span>
    </template>

    <TransitionGroup v-if="rows.length" tag="div" name="rank" class="rows">
      <button
        v-for="(r, i) in rows"
        :key="r.id"
        type="button"
        class="row"
        :class="[levelClass(r.level), blinkClass(r.level)]"
        :title="`${r.equipmentName} 설비 상세로 이동`"
        @click="emit('select', r)"
      >
        <span class="rank mono">{{ i + 1 }}</span>
        <span class="name"><b>{{ r.tag }}</b> {{ r.equipmentName }}</span>
        <span class="type">{{ sensorTypeOf(r.type)?.label ?? r.type }}</span>

        <span class="track">
          <span class="band" :style="{ left: at(r, r.warn), width: `calc(${at(r, r.alarm)} - ${at(r, r.warn)})` }" />
          <span class="band over" :style="{ left: at(r, r.alarm) }" />
          <span class="fill" :style="{ width: at(r, r.value) }" />
          <span class="tick warn" :style="{ left: at(r, r.warn) }" />
          <span class="tick alarm" :style="{ left: at(r, r.alarm) }" />
        </span>

        <span class="value">
          <b class="mono">{{ fmt(r) }}<small>{{ r.unit }}</small></b>
          <em>주의 기준 <i class="mono">{{ Math.round(r.ratio * 100) }}%</i></em>
        </span>
        <span class="go" aria-hidden="true">›</span>
      </button>
    </TransitionGroup>
    <p v-else class="empty">기준값이 있는 센서가 없습니다.</p>
  </BaseCard>
</template>

<style scoped>
/* 단계 색: 아래 요소들이 var(--c) 하나로 색을 받음 */
.ok {
  --c: var(--color-level-normal);
}

.warn {
  --c: var(--color-level-warn);
}

.alarm {
  --c: var(--color-level-alarm);
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: var(--c);
}

.rows {
  position: relative; /* 순위 이동 애니메이션 중 빠지는 줄의 기준 */
  padding: 4px 12px 6px 20px;
}

.row {
  width: 100%;
  height: 42px;
  display: grid;
  grid-template-columns: 16px 170px 38px 1fr 92px 14px;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.row:last-child {
  border-bottom: 0;
}

.row:hover {
  background: var(--color-surface-sub);
}

/*
 * 주의·경보 줄: 줄 바탕이 단계 색(옅은 색)으로 천천히 밝아졌다 사라지기를 반복
 * - 바탕 층(::before)을 따로 두고 투명도만 바꿈 → 마우스 올림 바탕·글자는 그대로
 * - 2026-11-20: 다른 점멸과 같은 박자로 (주의 1.6초 · 경보 0.8초 · 화재 0.6초, blink.css blink-fade)
 *   한 화면에서 3D 설비와 같이 깜빡이므로 세기는 그대로(약하게) 두고 박자만 맞춤
 */
.row.warn,
.row.alarm {
  position: relative;
  isolation: isolate; /* ::before 를 줄 바탕 위, 글자 아래에 둠 */
}

.row.warn::before,
.row.alarm::before {
  content: '';
  position: absolute;
  inset: 3px -8px;
  z-index: -1;
  border-radius: var(--radius-xs);
  background: var(--soft);
  box-shadow: inset 3px 0 0 var(--c); /* 왼쪽 단계 색 띠 */
  animation: blink-fade var(--blink-t) ease-in-out infinite;
  pointer-events: none;
}

/* 반짝일 때 가장 진한 바탕 = 단계 색 20% (tokens 의 -soft 색은 너무 옅어 반짝임이 잘 안 보임) */
.row.warn,
.row.alarm {
  --soft: color-mix(in srgb, var(--c) 20%, #fff);
}

.rank {
  font-size: 13px;
  color: var(--color-text-weak);
}

.name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.type {
  width: 38px;
  padding: 3px 0;
  border-radius: 4px;
  background: #f1f5f9;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  color: var(--color-text-sub);
}

/* 막대: 회색 바탕 + 주의~경보 구간(연한 주황) + 경보 넘는 구간(연한 빨강) + 지금 값 + 기준선 2개 */
.track {
  position: relative;
  height: 10px;
  border-radius: 5px;
  background: #f1f5f9;
}

.band {
  position: absolute;
  top: 0;
  bottom: 0;
  background: var(--color-level-warn-soft);
}

.band.over {
  right: 0;
  border-radius: 0 5px 5px 0;
  background: var(--color-level-alarm-soft);
}

.fill {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  border-radius: 5px;
  background: var(--c);
  transition:
    width var(--m-value),
    background var(--m-value);
}

.tick {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  background: var(--c);
}

.value {
  line-height: 1.15;
  text-align: right;
}

.value b {
  font-size: 16px;
  color: var(--c);
}

.value small {
  margin-left: 2px;
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--color-text-sub);
}

.value em {
  display: block;
  font-size: 11px;
  font-style: normal;
  color: #64748b;
}

.value em i {
  font-style: normal;
  font-weight: 600;
  color: var(--c);
}

.go {
  font-size: 18px;
  color: var(--color-text-weak);
}

.empty {
  padding: 40px 20px;
  font-size: 13px;
  text-align: center;
  color: var(--color-text-sub);
}

/* 순위가 바뀔 때: 자리 옮김(move), 새로 들어오는 줄은 옅게 나타나고 빠지는 줄은 옅게 사라짐 */
.rank-move {
  transition: transform var(--m-panel);
}

.rank-enter-active,
.rank-leave-active {
  transition: opacity var(--m-tab);
}

.rank-enter-from,
.rank-leave-to {
  opacity: 0;
}

.rank-leave-active {
  position: absolute;
  left: 20px;
  right: 12px;
}

@media (prefers-reduced-motion: reduce) {
  .rank-move,
  .rank-enter-active,
  .rank-leave-active,
  .fill {
    transition: none;
  }

  /* 움직임 줄이기 설정이면 반짝이지 않고 옅은 단계 색 바탕만 */
  .row.warn::before,
  .row.alarm::before {
    animation: none;
    opacity: 0.7;
  }
}
</style>
