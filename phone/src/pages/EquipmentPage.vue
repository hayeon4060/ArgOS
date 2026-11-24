<!--
  설비 상세 (화면설계 '주의 상세') — 공정 현황의 설비 배치도·설비 카드를 누르면 옴
  - 위: 단계(2단계 주의 등) · 발생 시각 · 설비 이름 · 어떤 값이 기준을 넘었는지
  - 센서: 지금 값 · 기준 · 평소 (이상 있는 센서 먼저)
  - 열화상: 공정 열화상 카메라 마지막 장면 + 최고 온도 지점 (웹 ThermalImage)
  - 체크리스트: 이 설비로 온 체크리스트가 있으면 요청자 · 보낸 시각 + 아래 '체크리스트 시작' 단추
  - 데이터: 웹 설비 상세와 같은 API — GET /equipment/{id}, /equipment/{id}/events, /thermal/{cameraId}/latest, /checklists/{id}
    값이 바뀌면(SSE, 지금은 예시 상황) 조용히 다시 불러옴
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getChecklist } from '@/api/checklists'
import { getEquipment, getEquipmentEvents, getThermalLatest } from '@/api/equipment'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import ThermalImage from '@/features/equipment/ThermalImage.vue'
import { useAuthStore } from '@/stores/auth'
import { LEVEL, LEVEL_META, SENSOR_STAGES, sensorTypeOf } from '@/types/codes'
import { useLiveReload } from '@/utils/useLiveReload'

const route = useRoute()
const auth = useAuthStore()

const equipment = ref(null)
const raisedAt = ref('') // 지금 단계가 된 시각 (정상이면 비움)
const thermal = ref(null)
const checklist = ref(null)
const errorMessage = ref('')

async function load() {
  const id = route.params.equipmentId
  try {
    const [eq, events] = await Promise.all([getEquipment(id), getEquipmentEvents(id)])
    const [t, c] = await Promise.all([
      eq.cameraId ? getThermalLatest(eq.cameraId, eq.id) : null,
      eq.activeChecklist ? getChecklist(eq.activeChecklist.id) : null,
    ])
    equipment.value = eq
    // 이력은 최근이 위 → 지금 단계와 같은 단계의 첫 알람 기록이 이번 발생
    raisedAt.value = eq.level === LEVEL.NORMAL ? '' : (events.find((e) => e.type === 'ALARM' && e.level === eq.level)?.at ?? '')
    thermal.value = t
    checklist.value = c
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error.message
  }
}

onMounted(load)
useLiveReload(load)

const hms = (iso) => iso?.slice(11, 19) ?? ''
const fmt = (value, type) => (value == null ? '-' : Number(value).toFixed(sensorTypeOf(type)?.dec ?? 0))
const typeLabel = (s) => sensorTypeOf(s.type)?.label ?? s.type

/** '2단계 주의' (정상·주의·경보 순서 번호). 정상·화재는 이름만 */
const badgeText = computed(() => {
  const level = equipment.value?.level
  const n = SENSOR_STAGES.indexOf(level) + 1
  return level !== LEVEL.NORMAL && n ? `${n}단계 ${LEVEL_META[level].label}` : ''
})

const RANK = { [LEVEL.FIRE]: 3, [LEVEL.ALARM]: 2, [LEVEL.WARN]: 1, [LEVEL.NORMAL]: 0 }

/** 센서 (이상 있는 센서 먼저): 기준 칸은 그 단계 기준(정상이면 주의 기준), 마지막 칸은 평소 값(없으면 경보 기준) */
const sensors = computed(() =>
  [...(equipment.value?.sensors ?? [])]
    .sort((a, b) => RANK[b.level] - RANK[a.level])
    .map((s) => ({
      ...s,
      limit: s.level === LEVEL.ALARM ? { label: '경보 기준', value: s.alarm } : { label: '주의 기준', value: s.warn },
      last: s.usual != null ? { label: '평소', value: s.usual } : { label: '경보 기준', value: s.alarm },
    })),
)

/** 무슨 일인지 한 줄씩: '전류 CT-17 값이 주의 기준(21.6 A)을 넘었습니다.' */
const reasons = computed(() =>
  sensors.value
    .filter((s) => s.level !== LEVEL.NORMAL)
    .map((s) => `${typeLabel(s)} ${s.tag} 값이 ${s.limit.label}(${s.limit.value} ${s.unit})을 넘었습니다.`),
)

/** 체크리스트 단추: 내 것이고 진행 중이면 시작 · 이어서, 아니면 보기만 */
const start = computed(() => {
  const c = checklist.value
  if (!c) return null
  const done = c.items.filter((i) => i.doneAt).length
  const mine = c.assignee.id === auth.user?.id && ['SENT', 'IN_PROGRESS'].includes(c.status)
  const label = !mine ? '체크리스트 보기' : done ? `이어서 하기 ${done}/${c.items.length}` : '체크리스트 시작'
  return { to: { name: 'checklist', params: { checklistId: c.id } }, label }
})
</script>

<template>
  <div>
    <p v-if="errorMessage" class="state">{{ errorMessage }}</p>
    <p v-else-if="!equipment" class="state">불러오는 중</p>

    <div v-else class="page">
      <div class="col">
        <!-- 무슨 일인지 -->
        <section class="head" :class="`is-${LEVEL_META[equipment.level]?.key}`">
          <div class="head-top">
            <LevelBadge :level="equipment.level" :text="badgeText" />
            <span v-if="raisedAt" class="at mono">{{ hms(raisedAt) }} 발생</span>
          </div>
          <h2>{{ equipment.name }}</h2>
          <p v-for="r in reasons" :key="r" class="reason">{{ r }}</p>
          <p v-if="!reasons.length" class="reason">모든 센서 값이 정상 범위입니다.</p>

          <ul class="sensors">
            <li v-for="s in sensors" :key="s.id" :class="`is-${LEVEL_META[s.level]?.key}`">
              <span class="cell">
                <small>{{ typeLabel(s) }} <span class="mono">{{ s.tag }}</span></small>
                <b class="mono now">{{ fmt(s.value, s.type) }}<em>{{ s.unit }}</em></b>
              </span>
              <span class="cell">
                <small>{{ s.limit.label }}</small>
                <b class="mono">{{ s.limit.value }}<em>{{ s.unit }}</em></b>
              </span>
              <span class="cell">
                <small>{{ s.last.label }}</small>
                <b class="mono">{{ fmt(s.last.value, s.type) }}<em>{{ s.unit }}</em></b>
              </span>
            </li>
            <li v-if="!sensors.length" class="none">붙은 센서가 없습니다.</li>
          </ul>
        </section>

        <!-- 요청자 -->
        <section v-if="checklist" class="req">
          <span class="req-icon"><AppIcon name="user" :size="18" /></span>
          <span class="req-text">
            <small>요청자</small>
            <b>{{ checklist.sentBy }} <span class="role">(공장 담당자)</span></b>
          </span>
          <span class="mono req-at">{{ hms(checklist.sentAt) }} 전송</span>
        </section>
      </div>

      <!-- 열화상 -->
      <section v-if="thermal" class="ir">
        <h3>열화상 이미지 <span class="mono">· {{ thermal.tag }}</span></h3>
        <ThermalImage
          :image-url="thermal.imageUrl"
          :max="thermal.max"
          :max-point="thermal.maxPoint"
          :scale="thermal.scale"
          :live="false"
          :zone-name="thermal.zone?.name ?? ''"
          :time="hms(thermal.capturedAt)"
          :label-offset="{ x: -120, y: 30 }"
        />
        <p class="ir-sub">{{ thermal.zone?.desc ?? '공정 열화상 카메라 1대가 공정 전체를 봅니다.' }}</p>
      </section>

      <RouterLink v-if="start" :to="start.to" class="btn btn-primary start">
        {{ start.label }}<AppIcon name="arrow" :size="18" />
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

section {
  padding: 16px;
  border: 1px solid var(--color-border-card);
  border-radius: 12px;
  background: var(--color-surface);
}

.head.is-warn {
  border-color: #fdba74;
}

.head.is-alarm,
.head.is-fire {
  border-color: #fca5a5;
}

.head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.at {
  font-size: 12.5px;
  color: var(--color-text-sub);
}

h2 {
  margin-top: 10px;
  font-size: 19px;
}

.reason {
  margin-top: 4px;
  font-size: 13.5px;
  line-height: 20px;
  color: var(--color-text-sub);
}

.sensors {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.sensors li {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}

.cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 10px;
  min-width: 0;
}

.cell + .cell {
  border-left: 1px solid var(--color-border);
}

.cell:first-child {
  padding-left: 0;
}

.cell small {
  font-size: 12px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.cell b {
  font-size: 18px;
}

.cell em {
  margin-left: 3px;
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
  color: var(--color-text-sub);
}

/* 센서 줄 단계만 (위 카드도 is-warn 이라 li 로 한정) */
li.is-warn .now {
  color: var(--color-level-warn-text);
}

li.is-alarm .now {
  color: var(--color-level-alarm-text);
}

.none {
  font-size: 13px;
  color: var(--color-text-sub);
}

.req {
  display: flex;
  align-items: center;
  gap: 12px;
}

.req-icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface-sub);
  color: var(--color-text-sub);
}

.req-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.req-text small {
  font-size: 12px;
  color: var(--color-text-sub);
}

.req-text b {
  font-size: 15px;
}

.role {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-sub);
}

.req-at {
  font-size: 12.5px;
  color: var(--color-text-sub);
}

h3 {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
}

h3 .mono {
  font-weight: 500;
  color: var(--color-text-sub);
}

.ir-sub {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

/* 아래 탭 바로 위에 붙어 있는 단추 (화면설계 '체크리스트 시작 →') */
.start {
  position: sticky;
  bottom: calc(72px + env(safe-area-inset-bottom));
  z-index: 10;
  width: 100%;
  height: 52px;
  border-radius: 12px;
  font-size: 16px;
  text-decoration: none;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
}

.state {
  margin-top: 20px;
  text-align: center;
  color: var(--color-text-sub);
}

/* 폴드 펼친 화면: 왼쪽 상황 · 요청자, 오른쪽 열화상 */
@media (min-width: 600px) {
  .page {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }

  .start {
    grid-column: 1 / -1;
    bottom: calc(16px + env(safe-area-inset-bottom));
  }
}
</style>
