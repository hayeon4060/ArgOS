<!--
  공정 현황 (수행계획서 '앱 화면' ① 설비 상태 정보, API 명세서 M1)
  - 경보·화재 띠: 담당 공정이 경보 이상이면 맨 위에 빨간 띠 (공장 담당자 지시 · 대피 안내)
  - 할 일: 진행 중 체크리스트 (누르면 체크리스트로)
  - 담당 공정 상태: 공정 단계 + 설비 단계별 개수
  - 설비 배치도: 웹과 같은 도장 공정 3D 배치도(PaintMap), 설비를 누르면 설비 상세
    휴대폰 폭에서는 크게 그려 좌우로 밀어 보게 하고, 처음에는 내 담당 설비(이상 있는 것 먼저)를 가운데로
  - 설비 목록: 내 담당 설비 먼저, 그 안에서 이상 있는 설비 먼저. 설비마다 센서 지금 값(단계 색)과 배기팬, 누르면 설비 상세
  - 데이터: GET /processes/{id}/layout · /status (웹 3D 배치도와 같은 API, processId 는 로그인 응답 scope.processIds)
    값이 바뀌면(SSE, 지금은 예시 상황) 조용히 다시 불러옴
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getProcessLayout, getProcessStatus } from '@/api/process'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import PaintMap from '@/features/process/PaintMap.vue'
import { drawingOf } from '@/features/process/paintLayout'
import { layoutPlacement } from '@/features/process/processEquipment'
import { useAuthStore } from '@/stores/auth'
import { LEVEL, LEVEL_META, SENSOR_STAGES } from '@/types/codes'
import { useLiveReload } from '@/utils/useLiveReload'
import { CHECKLIST_STATUS_LABEL } from '../codes'
import EquipmentCard from '../components/EquipmentCard.vue'
import { useMyChecklistsStore } from '../stores/myChecklists'

const auth = useAuthStore()
const my = useMyChecklistsStore()
const router = useRouter()

const processId = computed(() => auth.user?.scope?.processIds?.[0] ?? null)
const layout = ref(null)
const status = ref(null)
const errorMessage = ref('')

async function load() {
  if (!processId.value) {
    errorMessage.value = '배정된 공정이 없습니다. 공장 담당자에게 문의해 주세요.'
    return
  }
  try {
    const [l, s] = await Promise.all([getProcessLayout(processId.value), getProcessStatus(processId.value)])
    layout.value = l
    status.value = s
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error.message
  }
}

onMounted(load)
useLiveReload(load)

/** 단계 높은 순 (화재 > 경보 > 주의 > 정상) */
const RANK = { [LEVEL.FIRE]: 3, [LEVEL.ALARM]: 2, [LEVEL.WARN]: 1, [LEVEL.NORMAL]: 0 }

/** 내 담당 설비인지 (예시 데이터의 managers 는 id 가 0 이라 이름으로도 봄) */
const isMine = (e) => e.managers.some((m) => m.id === auth.user.id || m.name === auth.user.name)

/** 설비 + 지금 단계 + 센서 지금 값: [{ id, name, level, fan, mine, sensors: [{ tag, type, unit, value, level }] }] */
const equipment = computed(() => {
  if (!layout.value || !status.value) return []
  const eqStatus = Object.fromEntries(status.value.equipment.map((e) => [e.id, e]))
  const values = Object.fromEntries(status.value.sensors.map((s) => [s.id, s]))
  return layout.value.equipment
    .map((e) => ({
      id: e.id,
      ...drawingOf(e), // key(배치도 그림 안 이름) · shape — 공장 관리에서 추가한 설비도 그림에 (웹 processEquipment 와 같게)
      name: e.name,
      level: eqStatus[e.id]?.level ?? LEVEL.NORMAL,
      fan: eqStatus[e.id]?.fan ?? null,
      mine: isMine(e),
      sensors: layout.value.sensors.filter((s) => s.equipmentId === e.id).map((s) => ({ ...s, ...values[s.id] })),
    }))
    .sort((a, b) => b.mine - a.mine || RANK[b.level] - RANK[a.level])
})

const mine = computed(() => equipment.value.filter((e) => e.mine))
const others = computed(() => equipment.value.filter((e) => !e.mine))
const danger = computed(() => RANK[status.value?.level] >= RANK[LEVEL.ALARM])

/** 공장 관리에서 옮긴 설비 자리 · 사용 안 함 (웹 배치도와 같게) */
const placement = computed(() => layoutPlacement(layout.value))
const openEquipment = (e) => router.push({ name: 'equipment', params: { equipmentId: e.id } })

const mapScroll = ref(null)
const paintMap = ref(null)
/** 좌우로 밀 수 있을 때(휴대폰 폭) 내 담당 설비를 가운데로 */
function centerMap() {
  const box = mapScroll.value
  const target = mine.value[0]
  if (!box || !target || box.scrollWidth <= box.clientWidth) return
  const p = placement.value.positions[target.key] ?? paintMap.value.defaultPositions()[target.key]
  const vb = box.querySelector('svg')?.viewBox.baseVal
  if (!p || !vb?.width) return
  box.scrollLeft = ((p.x - vb.x) / vb.width) * box.scrollWidth - box.clientWidth / 2
}
watch(paintMap, (m) => m && centerMap(), { flush: 'post' })

/** 설비 단계별 개수 (정상·주의·경보) */
const counts = computed(() =>
  SENSOR_STAGES.map((level) => ({ level, ...LEVEL_META[level], n: equipment.value.filter((e) => e.level === level).length })),
)
</script>

<template>
  <div>
    <p v-if="danger" class="danger" role="alert">
      <AppIcon name="alert" :size="22" />
      <span>
        <b>{{ layout.name }} {{ LEVEL_META[status.level].label }} 발생</b>
        공장 담당자 지시에 따라 대응하고, 대피 안내가 나오면 바로 대피하세요.
      </span>
    </p>

    <!-- 할 일 -->
    <section class="todo">
      <RouterLink v-for="c in my.active" :key="c.id" :to="{ name: 'checklist', params: { checklistId: c.id } }" class="todo-card">
        <span class="todo-icon"><AppIcon name="clip" :size="22" /></span>
        <span class="todo-text">
          <span class="todo-head">
            <LevelBadge :level="c.level" />
            <span>{{ CHECKLIST_STATUS_LABEL[c.status] }} · 받은 시각 <span class="mono">{{ c.sentAt.slice(11, 16) }}</span></span>
          </span>
          <b>{{ c.title }}</b>
          <span class="todo-go">
            점검 체크리스트 <span class="mono">{{ c.doneCount }}/{{ c.totalCount }}</span> · {{ c.doneCount ? '이어서 하기' : '시작하기' }}
          </span>
        </span>
        <AppIcon name="cr" :size="20" class="chev" />
      </RouterLink>
      <p v-if="my.loaded && !my.active.length" class="todo-none"><AppIcon name="check" :size="18" />받은 점검 체크리스트가 없습니다.</p>
    </section>

    <p v-if="errorMessage" class="state">{{ errorMessage }}</p>
    <p v-else-if="!status" class="state">불러오는 중</p>

    <template v-else>
      <!-- 담당 공정 상태 -->
      <section class="summary" :class="`is-${LEVEL_META[status.level]?.key}`">
        <div class="summary-head">
          <span>
            <small>담당 공정</small>
            <b>{{ layout.name }}</b>
          </span>
          <LevelBadge :level="status.level" />
        </div>
        <ul class="counts">
          <li v-for="c in counts" :key="c.level" :class="`is-${c.key}`">
            <span class="dot" />{{ c.label }} <b class="mono">{{ c.n }}</b>
          </li>
        </ul>
      </section>

      <!-- 3D 설비 배치도 -->
      <section class="map-card">
        <div class="map-head">
          <b>설비 배치도</b>
          <span>설비를 누르면 상세</span>
        </div>
        <div ref="mapScroll" class="map-scroll">
          <div class="map">
            <PaintMap
              ref="paintMap"
              :equipment="equipment"
              :positions="placement.positions"
              :inactive="placement.inactive"
              :hidden="placement.hidden"
              @select="openEquipment"
            />
          </div>
        </div>
      </section>

      <h2>내 담당 설비 <small>{{ mine.length }}</small></h2>
      <div class="grid">
        <RouterLink v-for="e in mine" :key="e.id" :to="{ name: 'equipment', params: { equipmentId: e.id } }" class="eq-link">
          <EquipmentCard :equipment="e" />
        </RouterLink>
      </div>
      <p v-if="!mine.length" class="state small">담당으로 지정된 설비가 없습니다.</p>

      <h2>공정의 다른 설비 <small>{{ others.length }}</small></h2>
      <div class="grid">
        <RouterLink v-for="e in others" :key="e.id" :to="{ name: 'equipment', params: { equipmentId: e.id } }" class="eq-link">
          <EquipmentCard :equipment="e" compact />
        </RouterLink>
      </div>
    </template>
  </div>
</template>

<style scoped>
.danger {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  padding: 14px;
  border-radius: 12px;
  background: var(--color-level-alarm);
  color: #fff;
  font-size: 13.5px;
  line-height: 20px;
  animation: danger-pulse var(--m-pulse-alarm) ease-in-out infinite alternate;
}

.danger b {
  display: block;
  font-size: 16px;
}

.todo {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #fdba74;
  border-radius: 12px;
  background: var(--color-level-warn-soft);
  color: var(--color-text);
  text-decoration: none;
  animation: rise var(--m-rise) both;
}

.todo-icon {
  width: 42px;
  height: 42px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--color-level-warn);
  color: #fff;
}

.todo-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.todo-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-sub);
}

.todo-text b {
  font-size: 15.5px;
}

.todo-go {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-level-warn-text);
}

.chev {
  color: var(--color-level-warn-text);
}

.todo-none {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px;
  border-radius: 12px;
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
  font-size: 14px;
  font-weight: 600;
}

.summary {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--color-border-card);
  border-left: 5px solid var(--color-level-normal);
  border-radius: 12px;
  background: var(--color-surface);
}

.summary.is-warn {
  border-left-color: var(--color-level-warn);
}

.summary.is-alarm,
.summary.is-fire {
  border-left-color: var(--color-level-alarm);
}

.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary-head small {
  display: block;
  font-size: 12px;
  color: var(--color-text-sub);
}

.summary-head b {
  font-size: 19px;
}

.counts {
  display: flex;
  gap: 16px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: var(--color-text-sub);
}

.counts li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.counts b {
  color: var(--color-text);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-level-normal);
}

.counts .is-warn .dot {
  background: var(--color-level-warn);
}

.counts .is-alarm .dot {
  background: var(--color-level-alarm);
}

.map-card {
  margin-top: 12px;
  padding: 14px 14px 10px;
  border: 1px solid var(--color-border-card);
  border-radius: 12px;
  background: var(--color-surface);
}

.map-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 15px;
}

.map-head span {
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 휴대폰 폭: 카드 끝까지 넓혀 크게 그리고 좌우로 밀어 봄 */
.map-scroll {
  margin: 6px -14px 0;
  overflow-x: auto;
  scrollbar-width: none;
}

/* 웹 배치도 칸 비율 (1236 × 790) */
.map {
  width: 170%;
  aspect-ratio: 1236 / 790;
}

.eq-link {
  display: block;
  color: inherit;
  text-decoration: none;
}

h2 {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 22px 0 10px;
  font-size: 15px;
}

h2 small {
  font-size: 13px;
  color: var(--color-text-weak);
}

.state {
  margin-top: 20px;
  text-align: center;
  color: var(--color-text-sub);
}

.state.small {
  margin-top: 0;
  font-size: 13px;
}

/* 폴드 펼친 화면: 할 일 여러 개·설비 카드를 두 줄로 */
@media (min-width: 600px) {
  .todo,
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 12px;
    align-items: start;
  }

  .grid :deep(.eq) {
    margin-bottom: 0;
  }

  /* 펼친 화면: 배치도 전체를 한 번에 */
  .map-scroll {
    margin: 6px 0 0;
    overflow: visible;
  }

  .map {
    width: auto;
    max-width: 900px;
    margin: 0 auto;
  }
}

@keyframes danger-pulse {
  to {
    background: #b91c1c;
  }
}

@media (prefers-reduced-motion: reduce) {
  .danger {
    animation: none;
  }
}
</style>
