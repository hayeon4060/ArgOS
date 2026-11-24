<!--
  배치도 아래 설비 줄 (설계안 vProcess() 의 #eqs): 공정 흐름 순서대로 설비 카드
  - 카드마다 번호, 이름, 상태 배지
  - 펼친 카드 1장: 넓어지면서 센서 값과 원형 게이지, '설비 상세'가 보임
    · 마우스를 올린(또는 키보드로 고른) 카드가 펼쳐짐
    · 마우스를 빼도 마지막으로 올린 카드가 펼쳐진 채로 남음 (2026-11-18 재원님 요청)
    · 화면에 처음 들어왔을 때(아직 아무 카드에도 올리지 않았을 때)만 기본 카드
      = 단계가 가장 높은 설비(주의·경보 중인 설비), 모두 정상이면 마지막 순서 설비
    · 체크리스트가 진행 중이면 '체크리스트 2/5' 칩 → 누르면 체크리스트 진행 창 (카드 이동은 안 함)
  - 누르면 select 이벤트
  - 펼친 카드는 오른쪽 끝이 그대로이거나 왼쪽 끝이 그대로인 채로 넓어지므로
    마우스 아래 카드가 바뀌어 흔들리는 일이 없음
-->
<script setup>
import { computed, ref } from 'vue'
import SensorRing from './SensorRing.vue'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { keyOf } from '@/features/site/stages'
import { useAlertStore } from '@/stores/alert'
import { LEVEL, SENSOR_TYPES } from '@/types/codes'
import { blinkClass } from '@/utils/blink'

const props = defineProps({
  /** 화면용 설비 목록 (seq 가 있는 것만 줄에 나옴) */
  equipment: { type: Array, required: true },
})

const emit = defineEmits(['select'])

// 순서 번호가 있는 설비만, 번호 순서대로
const line = computed(() => props.equipment.filter((e) => e.seq).sort((a, b) => a.seq - b.seq))

// 단계 높낮이 (정상 0 → 화재 3)
const LEVEL_RANK = Object.values(LEVEL)
const rank = (level) => LEVEL_RANK.indexOf(level)

// 기본으로 펼칠 카드: 단계가 가장 높은 설비, 모두 정상이면 마지막 순서
const defaultId = computed(() => {
  const list = line.value
  if (!list.length) return null
  const worst = list.reduce((a, b) => (rank(b.level) > rank(a.level) ? b : a), list[0])
  return worst.level !== LEVEL.NORMAL ? worst.id : list.at(-1).id
})

// 마지막으로 마우스를 올린(키보드로 고른) 카드. 마우스를 빼도 지우지 않음
// 아직 고른 적이 없으면(null) 기본 카드
const pickedId = ref(null)
const openId = computed(() => pickedId.value ?? defaultId.value)

// 펼친 카드의 센서: 전류·온도·CO 순서
const sensorsOf = (eq) => SENSOR_TYPES.map((t) => eq.sensors.find((s) => s.type === t.type)).filter(Boolean)
const typeLabel = (type) => SENSOR_TYPES.find((t) => t.type === type)?.label ?? type

// 진행 중 체크리스트 칩 (상단 바 체크리스트와 같은 건)
const alert = useAlertStore()
// 경보·화재 중에는 숨김 (상단 바 체크리스트 버튼과 같은 규칙)
const emergency = computed(() => ['ALARM', 'FIRE'].includes(alert.primary?.level))
const checklist = computed(() => (openId.value && !emergency.value ? alert.checklistOfEquipment(openId.value) : null))
const checklistText = computed(() => {
  const c = checklist.value
  if (!c) return ''
  // 서버 목록 줄은 항목(items) 없이 doneCount · totalCount 만 줌
  return c.status === 'REPORTED' ? '체크리스트 완료 · 확인 필요' : `체크리스트 ${c.doneCount}/${c.totalCount}`
})
function openChecklist() {
  alert.open('checklist', { alarmId: checklist.value.alarmId, checklistId: checklist.value.id })
}
</script>

<template>
  <div class="strip">
    <button
      v-for="eq in line"
      :key="eq.id"
      type="button"
      class="item"
      :class="[`is-${keyOf(eq.level)}`, { open: eq.id === openId }, blinkClass(eq.level) && ['blink-glow', 'weak', blinkClass(eq.level)]]"
      @mouseenter="pickedId = eq.id"
      @focus="pickedId = eq.id"
      @click="emit('select', eq)"
    >
      <span class="head">
        <span class="no mono">{{ eq.seq }}</span>
        <b>{{ eq.name }}</b>
        <LevelBadge :level="eq.level" />
        <template v-if="eq.id === openId">
          <!-- 버튼 안 버튼을 피하려고 span + role. 눌러도 카드의 설비 상세 이동은 막음(.stop) -->
          <span
            v-if="checklist"
            class="ck-chip"
            :class="{ done: checklist.status === 'REPORTED' }"
            role="button"
            tabindex="0"
            @click.stop="openChecklist"
            @keydown.enter.stop.prevent="openChecklist"
          >
            <AppIcon name="clip" :size="13" />{{ checklistText }}
          </span>
          <span class="go">설비 상세 <AppIcon name="cr" :size="16" /></span>
        </template>
      </span>

      <!-- 펼친 카드에만: 센서 값 + 원형 게이지 -->
      <span v-if="eq.id === openId" class="sensors">
        <span v-for="s in sensorsOf(eq)" :key="s.tag" class="sensor">
          <SensorRing :sensor="s" :size="44" />
          <span>
            <small>{{ typeLabel(s.type) }} <span class="mono">{{ s.tag }}</span></small>
            <b class="mono" :class="{ [`is-${keyOf(s.level)}`]: s.level !== LEVEL.NORMAL }">
              {{ s.value }}<small> {{ s.unit }}</small>
            </b>
          </span>
        </span>
        <span v-if="!eq.sensors.length" class="empty">연결된 센서 없음</span>
      </span>
    </button>
  </div>
</template>

<style scoped>
.strip {
  display: flex;
  gap: 10px;
  padding: 0 20px 18px;
}

/* 접힌 카드: 남는 폭을 똑같이 나눠 가짐 */
.item {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 11px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fff;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  /* 넓어지고 좁아지는 움직임 (tokens.css --m-panel) */
  transition:
    flex-grow var(--m-panel),
    min-width var(--m-panel),
    border-color var(--m-hover),
    background-color var(--m-hover),
    box-shadow var(--m-hover);
}

.item:hover,
.item:focus-visible {
  border-color: #93c5fd;
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.12);
}

/* 펼친 카드: 센서 3개(게이지 44px + 값)가 다 들어가는 폭(372px)보다 줄어들지 않게 */
.item.open {
  flex-grow: 3.4;
  min-width: 372px;
  padding: 11px 14px;
}

/* 펼친 카드가 주의·경보면 테두리와 바탕을 단계 색으로 */
.item.open.is-warn {
  border-color: var(--color-level-warn);
  background: rgba(255, 237, 213, 0.4);
}

.item.open.is-alarm,
.item.open.is-fire {
  border-color: var(--color-level-alarm);
  background: rgba(254, 226, 226, 0.4);
}

/* 접힌 카드: 이름 아래 배지 / 펼친 카드: 한 줄에 번호·이름·배지·칩·설비 상세 */
.head {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.item.open .head {
  flex-direction: row;
  align-items: center;
}

.head b {
  max-width: 100%;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 파란 동그라미 번호 */
.no {
  flex: none;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
}

/* 체크리스트 진행 칩 (설계안 .chip.info.lk) */
.ck-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
  cursor: pointer;
  animation: pop-in var(--m-pop-in) both; /* motion.css */
  transition: box-shadow var(--m-hover);
}

.ck-chip:hover {
  box-shadow: 0 0 0 3px rgba(3, 105, 161, 0.18);
}

.ck-chip.done {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

.go {
  margin-left: auto;
  display: flex;
  align-items: center;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

/* 센서 줄: 펼쳐질 때 살짝 늦게 나타남 (카드가 넓어진 뒤) */
.sensors {
  display: flex;
  gap: 12px;
  animation: pop-in var(--m-panel) 80ms both; /* motion.css */
}

.sensor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sensor small {
  display: block;
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}

.sensor b {
  font-size: 16px;
  white-space: nowrap;
}

.sensor b small {
  display: inline;
  color: var(--color-text-weak);
}

.sensor b.is-warn {
  color: var(--color-level-warn-text);
}

.sensor b.is-alarm,
.sensor b.is-fire {
  color: var(--color-level-alarm-text);
}

.empty {
  height: 44px;
  display: flex;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 움직임 줄이기 설정이면 넓어지는 움직임 없이 바로 바뀜 */
@media (prefers-reduced-motion: reduce) {
  .item {
    transition: none;
  }

  .sensors {
    animation: none;
  }
}
</style>
