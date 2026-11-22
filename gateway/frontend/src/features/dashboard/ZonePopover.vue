<!--
  지도에서 공장에 마우스를 올렸을 때 뜨는 말풍선 (설계안 v-dash.js vSite() 의 pop)
  - 2026-11-20: 누르기 → 마우스 올리기로 바뀌어 '공정 배치도 보기' 단추 삭제 (공장을 누르면 바로 이동)
    말풍선은 마우스를 받지 않음(pointer-events: none) — 말풍선이 지도를 가려 깜빡이는 것을 막음
  - 들어갈 수 있는 공장(5공장): 공정 목록과 단계
  - 그 외 공장: 오늘 알림 건수 + '아직 등록되지 않음' 안내
  - 위치: anchor(구역 가운데, %) 의 오른쪽에 띄움 (2026-11-19 재원님 요청: 왼쪽 말고 오른쪽)
    오른쪽 자리가 모자라면 점과의 간격(70px)을 줄여서 지도 안에 넣고, 그래도 안 들어갈 때만 왼쪽에 띄움
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { keyOf } from '@/features/site/stages'
import { LEVEL_META } from '@/types/codes'
import { blinkClass } from '@/utils/blink'

const props = defineProps({
  /** 사업장 현황의 공장 하나 */
  factory: { type: Object, required: true },
  /** 구역 가운데 위치 { x, y } (%) */
  anchor: { type: Object, required: true },
  /** 공정 배치도로 들어갈 수 있는 공장인지 */
  enterable: { type: Boolean, default: false },
})

/* 말풍선 자리: 점에서 70px 띄우고 너비 292px (아래 CSS 와 같은 값), 간격은 24px 까지 줄일 수 있음 */
const GAP = 70
const MIN_GAP = 24
const WIDTH = 292

// 지도(말풍선이 놓이는 칸) 너비 — 창 크기가 바뀌면 다시 잼
const root = ref(null)
const mapWidth = ref(0)
const measure = () => (mapWidth.value = root.value?.offsetParent?.clientWidth ?? 0)
onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
})
onBeforeUnmount(() => window.removeEventListener('resize', measure))

// 오른쪽에 둘 때 점과의 간격: 지도 오른쪽 끝(여백 8px)을 넘지 않게 70px 에서 줄임
const gap = computed(() => {
  if (!mapWidth.value) return GAP
  const dotX = (props.anchor.x / 100) * mapWidth.value
  return Math.min(GAP, mapWidth.value - 8 - WIDTH - dotX)
})
// 간격을 줄여도 안 들어갈 때만 왼쪽에
const onLeft = computed(() => gap.value < MIN_GAP)

const style = computed(() => ({
  left: `${props.anchor.x}%`,
  top: `${props.anchor.y}%`,
}))
// 말풍선: 오른쪽이면 점에서 gap 만큼, 왼쪽이면 CSS(.left) 값
const popStyle = computed(() => (onLeft.value ? style.value : { ...style.value, marginLeft: `${gap.value}px` }))
</script>

<template>
  <!-- 구역 가운데 점 -->
  <span class="dot" :style="{ ...style, background: LEVEL_META[factory.level].color }" />

  <div ref="root" class="zone-popover" :class="{ left: onLeft }" :style="popStyle">
    <div class="head">
      {{ factory.name }}
      <LevelBadge :level="factory.level" />
      <small>{{ enterable ? `공정 ${factory.processes.length}개` : `센서 ${factory.sensorCount}개` }}</small>
    </div>

    <template v-if="enterable">
      <div v-for="p in factory.processes" :key="p.id" class="row" :class="[`is-${keyOf(p.level)}`, blinkClass(p.level) && ['blink-row', 'weak', blinkClass(p.level)]]">
        <i :style="{ background: LEVEL_META[p.level].color }" />
        <span class="name">{{ p.name }}</span>
        <LevelBadge :level="p.level" />
      </div>
    </template>

    <template v-else>
      <div class="row">
        <span class="name sub">금일 알림</span>
        <b class="mono">{{ factory.todayAlarmCount }}건</b>
      </div>
      <div class="row note">
        <AppIcon name="info" :size="15" />
        <span>공정 배치도가 아직 등록되지 않았습니다. 지금은 5공장만 볼 수 있습니다.</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dot {
  position: absolute;
  width: 10px;
  height: 10px;
  border: 2px solid #fff;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

/* 말풍선: 기본은 점 오른쪽 70px, 자리가 없을 때만 점 왼쪽 70px (.left) */
.zone-popover {
  position: absolute;
  z-index: 4;
  width: 292px;
  margin-left: 70px;
  transform: translateY(-50%);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
  animation: pop-in var(--m-pop-in) both; /* 4px 위에서 내려오며 나타남 (motion.css) */
  pointer-events: none; /* 마우스는 아래 지도가 받음 */
}

.zone-popover.left {
  margin-left: -362px;
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px 9px;
  font-size: 15px;
  font-weight: 700;
}

.head small {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-weak);
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  border-top: 1px solid #f1f5f9;
  font-size: 14px;
}

.row i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.row .name {
  flex: 1;
}

.row .sub {
  color: var(--color-text-sub);
}

/* 주의·경보 공정은 옅은 배경 + 왼쪽 색 띠 */
.row.is-warn {
  background: rgba(255, 237, 213, 0.4);
  box-shadow: inset 3px 0 0 var(--color-level-warn);
  font-weight: 700;
}

.row.is-alarm,
.row.is-fire {
  background: rgba(254, 226, 226, 0.4);
  box-shadow: inset 3px 0 0 var(--color-level-alarm);
  font-weight: 700;
}

.note {
  align-items: flex-start;
  font-size: 13px;
  line-height: 19px;
  color: #64748b;
}

.note .app-icon {
  margin-top: 2px;
  color: var(--color-text-weak);
}
</style>
