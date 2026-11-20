<!--
  사업장 목록의 사업장 한 칸
  - 머리줄: 펼침 표시, 이름·상태·코드, 주소·위경도, 센서 단계 개수, '이동' 버튼
  - 펼치면: 공장 표 (FactoryRow 여러 줄)
  - 이벤트: toggle(펼치기/접기), enter(사업장으로 이동), enter-factory(공장으로 이동)
  - 2026-11-20: 공장 관리에서 막 만든 사업장은 주소 · 위경도가 없음(null) → '위치 미등록', 공장이 없으면 안내 한 줄
-->
<script setup>
import FactoryRow from './FactoryRow.vue'
import StageCounts from './StageCounts.vue'
import { STAGES, keyOf } from './stages'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { blinkClass } from '@/utils/blink'

defineProps({
  /** GET /sites 의 사업장 하나 */
  site: { type: Object, required: true },
  open: { type: Boolean, default: false },
  /** 파란 테두리 + '이동' 버튼 강조 (진입 가능한 사업장) */
  current: { type: Boolean, default: false },
  /** 펼쳤을 때의 공장 목록: { loading, error, factories } (useSiteTrees) */
  tree: { type: Object, default: null },
  /** 누를 수 있는 공장 ID */
  linkFactoryId: { type: Number, default: null },
})

const emit = defineEmits(['toggle', 'enter', 'enter-factory'])
</script>

<template>
  <!-- 주의·경보·화재 사업장은 칸 바깥으로 약하게 빛번짐 (blink.css blink-glow weak) -->
  <div class="site" :class="[`is-${keyOf(site.level)}`, { open, current }, blinkClass(site.level) && ['blink-glow', 'weak', blinkClass(site.level)]]">
    <!-- 머리줄 (누르면 펼치기/접기) -->
    <div
      class="head"
      role="button"
      tabindex="0"
      :aria-expanded="open"
      @click="emit('toggle', site.id)"
      @keydown.enter="emit('toggle', site.id)"
    >
      <AppIcon name="cr" :size="16" class="chev" />
      <div class="name">
        <b>{{ site.name }}</b>
        <LevelBadge :level="site.level" />
        <span class="mono code">{{ site.code }}</span>
      </div>
      <div class="loc">
        <AppIcon name="pin" :size="15" />
        <span>{{ site.address ?? '주소 미등록' }}</span>
        <span v-if="site.lat != null && site.lng != null" class="mono">{{ site.lat.toFixed(4) }}°N, {{ site.lng.toFixed(4) }}°E</span>
      </div>
      <StageCounts :counts="site.sensorLevels" size="lg" />
      <button type="button" class="btn" :class="{ 'btn-primary': current }" @click.stop="emit('enter', site.id)">
        이동 <AppIcon name="arrow" :size="16" />
      </button>
    </div>

    <!-- 공장 표 (펼쳤을 때) -->
    <div class="body">
      <div class="table">
        <div class="th">
          <span>공장</span>
          <span>공정별 단계</span>
          <span>센서 단계</span>
          <!-- 정상·주의·경보 숫자 칸마다 머리글 (색 점 + 이름) -->
          <span v-for="s in STAGES" :key="s.level" class="r">
            <i :style="{ background: s.color }" />{{ s.label }}
          </span>
          <span class="r">오늘 알림</span>
        </div>
        <p v-if="tree?.loading" class="state">불러오는 중</p>
        <p v-else-if="tree?.error" class="state error">{{ tree.error }}</p>
        <p v-if="tree?.factories && !tree.factories.length" class="state">아직 공장이 없습니다. '이동'을 누르면 공장 관리에서 공장을 추가할 수 있습니다.</p>
        <FactoryRow
          v-for="factory in tree?.factories ?? []"
          :key="factory.id"
          :factory="factory"
          :link="factory.id === linkFactoryId"
          @enter="emit('enter-factory', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.site {
  margin-bottom: 12px;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  background: #fff;
  overflow: hidden;
}

.site.current {
  border: 1.5px solid var(--color-primary);
}

.site.is-warn {
  border-color: var(--color-level-warn);
}

.site.is-alarm,
.site.is-fire {
  border-color: var(--color-level-alarm);
}

/* 머리줄 */
.head {
  height: 76px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px 0 14px;
  cursor: pointer;
}

.head:hover {
  background: var(--color-surface-sub);
}

.chev {
  color: var(--color-text-weak);
  transition: transform 0.2s ease-out;
}

.open .chev {
  transform: rotate(90deg);
}

.name {
  width: 230px;
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.name b {
  font-size: 18px;
}

.code {
  padding: 1px 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  font-size: 12px;
  color: var(--color-text-weak);
}

.loc {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-left: 22px;
  font-size: 13px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.loc span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.loc .app-icon {
  position: absolute;
  left: 0;
  top: 2px;
  color: var(--color-text-weak);
}

.loc .mono {
  font-size: 12px;
  color: var(--color-text-weak);
}

.head .btn {
  height: 38px;
  padding: 0 14px;
}

/* 펼침: 높이 0 ↔ 내용 높이 (설계안과 같은 grid 방식) */
.body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows var(--m-panel); /* tokens.css */
}

.open .body {
  grid-template-rows: 1fr;
}

/*
 * 공장 표 칸 너비 (머리줄 .th 와 FactoryRow 가 같이 씀)
 * 공장 | 공정별 단계 | 센서 막대 | 정상 | 주의 | 경보 | 오늘 알림
 * - 남는 폭은 공장 : 공정 : 막대 = 1 : 1.25 : 0.7 로 나눠서, 공정 칩 뒤나 막대만 길어지지 않게 함
 * - 숫자 3칸·오늘 알림은 고정 폭 (오른쪽 정렬로 머리글과 줄 맞춤)
 */
.table {
  --factory-cols: minmax(140px, 1fr) minmax(240px, 1.25fr) minmax(120px, 0.7fr) 48px 48px 48px 64px;
  min-height: 0;
  overflow: hidden;
}

.th {
  height: 34px;
  display: grid;
  grid-template-columns: var(--factory-cols);
  align-items: center;
  gap: 16px;
  padding: 0 16px 0 44px;
  background: var(--color-surface-sub);
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-weak);
  white-space: nowrap;
}

/* 숫자 칸 머리글: 오른쪽 정렬 + 색 점 */
.th .r {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
}

.th .r i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
}

.state {
  padding: 12px 16px 12px 44px;
  border-top: 1px solid #f1f5f9;
  font-size: 13px;
  color: var(--color-text-sub);
}

.state.error {
  color: var(--color-level-alarm-text);
}
</style>
