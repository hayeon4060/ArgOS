<!--
  사업장 목록의 공장 한 줄: 공장 이름 | 공정별 단계 칩 | 센서 단계 막대 | 정상 | 주의 | 경보 | 오늘 알림
  - 칸 너비는 SiteListItem 의 .table 에 있는 --factory-cols 를 같이 씀 (머리줄과 항상 같은 칸)
  - link: 들어갈 수 있는 공장이면 누를 수 있게 표시하고, 누르면 enter 이벤트
-->
<script setup>
import StageBar from './StageBar.vue'
import { keyOf, shortName } from './stages'
import { LEVEL, LEVEL_META } from '@/types/codes'
import { blinkClass } from '@/utils/blink'

const props = defineProps({
  /** { id, name, level, processes, counts, todayAlarmCount } */
  factory: { type: Object, required: true },
  link: { type: Boolean, default: false },
})

const emit = defineEmits(['enter'])

function onEnter() {
  if (props.link) emit('enter', props.factory)
}
</script>

<template>
  <div
    class="factory-row"
    :class="[`is-${keyOf(factory.level)}`, { link }, blinkClass(factory.level) && ['blink-row', 'weak', blinkClass(factory.level)]]"
    :role="link ? 'button' : undefined"
    :tabindex="link ? 0 : undefined"
    @click="onEnter"
    @keydown.enter="onEnter"
  >
    <span class="name">
      <i :style="{ background: LEVEL_META[factory.level].color }" />{{ factory.name }}
    </span>

    <span class="procs">
      <em v-for="p in factory.processes" :key="p.id" :class="`is-${keyOf(p.level)}`">
        <i />{{ shortName(p.name) }}
      </em>
    </span>

    <!-- 센서 단계: 막대 한 칸 + 숫자 3칸 (숫자마다 머리글이 따로 있음). 0은 흐리게 -->
    <StageBar :counts="factory.counts" />
    <b class="mono num" :class="{ zero: !factory.counts[LEVEL.NORMAL] }">{{ factory.counts[LEVEL.NORMAL] }}</b>
    <b class="mono num" :class="factory.counts[LEVEL.WARN] ? 'is-warn' : 'zero'">{{ factory.counts[LEVEL.WARN] }}</b>
    <b class="mono num" :class="factory.counts[LEVEL.ALARM] ? 'is-alarm' : 'zero'">{{ factory.counts[LEVEL.ALARM] }}</b>

    <span class="mono alarms" :class="{ zero: !factory.todayAlarmCount }">{{ factory.todayAlarmCount }}<small>건</small></span>
  </div>
</template>

<style scoped>
/* 칸 너비: SiteListItem 의 --factory-cols (머리줄과 같은 값) */
.factory-row {
  height: 46px;
  display: grid;
  grid-template-columns: var(--factory-cols);
  align-items: center;
  gap: 16px;
  padding: 0 16px 0 44px;
  border-top: 1px solid #f1f5f9;
  font-size: 14px;
}

.link {
  cursor: pointer;
}

.link:hover {
  background: var(--color-surface-sub);
}

.factory-row.is-warn {
  background: #fff7ed;
}

.factory-row.is-alarm,
.factory-row.is-fire {
  background: #fef2f2;
}

.name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  white-space: nowrap;
}

.name i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

/* 공정 칩: 정상은 회색 테두리, 주의·경보는 상태 색 */
.procs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

em {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  background: #fff;
  font-size: 12.5px;
  font-style: normal;
  color: var(--color-text-sub);
  white-space: nowrap;
}

em i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-level-normal);
}

em.is-warn {
  border-color: var(--color-level-warn);
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
  font-weight: 700;
}

em.is-warn i {
  background: var(--color-level-warn);
}

em.is-alarm,
em.is-fire {
  border-color: var(--color-level-alarm);
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
  font-weight: 700;
}

em.is-alarm i,
em.is-fire i {
  background: var(--color-level-alarm);
}

/* 숫자 칸: 머리글과 같이 오른쪽 정렬 */
.num {
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

/* 0 은 흐리게 → 주의·경보가 있는 칸만 눈에 들어옴 */
.num.zero,
.alarms.zero,
.alarms.zero small {
  color: #cbd5e1;
}

.num.is-warn {
  color: var(--color-level-warn-text);
}

.num.is-alarm {
  color: var(--color-level-alarm-text);
}

.alarms {
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

/* '건' 은 숫자보다 작고 흐리게 */
.alarms small {
  margin-left: 1px;
  font-family: var(--font-sans);
  font-size: 11.5px;
  font-weight: 500;
  color: var(--color-text-weak);
}
</style>
