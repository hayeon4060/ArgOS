<!--
  상단 바 알림 종을 누르면 열리는 알림 목록 (화면설계안 js/shell.js #bell onclick · css/extra.css .menu.wide · css/base.css .al)
  - 머리: '알림' + 오른쪽 '오늘'
  - 줄 (최근 4건, 설계안 alertRows().slice(0, 4)): 센서 종류 아이콘 · 제목 · 위치 · 센서 값 / 진행 상태 칩 · 발생 시각
    조치가 끝나지 않은 알림은 왼쪽 색 띠(깜빡임) + 옅은 배경 (오늘 알림 내역 카드와 같은 규칙: features/alert/alarmView.js)
  - 알림이 없으면 '새 알림이 없습니다'
  - 아래: '알림 이력 전체 보기 ›' → 통계·이력 화면
    사업장을 고르기 전(본사 관리자의 사업장 선택 화면)에는 갈 사업장이 없어 흐린 안내 글자로 바뀜 (설계안 .pgo.no 모양)
  - 데이터: GET /alarms?date=today&size=4 (공장 담당자는 담당 공장만 — stores/alert 와 같은 범위)
  - 열고 닫기 · 바깥 누르면 닫기는 부모(AppHeaderBadges)가 함
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { getAlarms } from '@/api/dashboard'
import { alarmChipOf, alarmIconOf, alarmTimeOf, isActiveAlarm, levelKeyOf } from '@/features/alert/alarmView'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'
import { useLiveReload } from '@/utils/useLiveReload'

const emit = defineEmits(['close'])

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 설계안처럼 최근 4건만 */
const SIZE = 4

const rows = ref(null) // null: 불러오는 중
const failed = ref(false)

/** 지금 보고 있는 사업장 (사업장 선택 화면에서는 없음) */
const siteId = computed(() => route.params.siteId ?? null)

/** 불러올 범위: 공장 담당자는 담당 공장, 본사 관리자는 보고 있는 사업장(없으면 전체) */
function scope() {
  const factoryId = auth.user?.scope?.factoryIds?.[0]
  if (auth.role === ROLE.PLANT_ADMIN && factoryId) return { factoryId }
  return siteId.value ? { siteId: Number(siteId.value) } : {}
}

async function load() {
  try {
    const res = await getAlarms({ ...scope(), size: SIZE })
    rows.value = res.items.slice(0, SIZE)
    failed.value = false
  } catch {
    failed.value = true
    rows.value = rows.value ?? []
  }
}

onMounted(load)
useLiveReload(load) // 열어 둔 사이 알림이 바뀌면 목록도 다시

/** 줄 하나에 보일 값 */
const view = (a) => ({
  key: a.id,
  active: isActiveAlarm(a),
  level: levelKeyOf(a),
  icon: alarmIconOf(a),
  chip: alarmChipOf(a),
  time: alarmTimeOf(a),
  place: [a.location, [a.sensorTag, a.value != null ? `${a.value} ${a.unit ?? ''}`.trim() : ''].filter(Boolean).join(' ')]
    .filter(Boolean)
    .join(' · '),
  title: a.title,
})
const items = computed(() => (rows.value ?? []).map(view))

/** '알림 이력 전체 보기' → 통계·이력 화면 */
function goHistory() {
  if (!siteId.value) return
  emit('close')
  router.push({ name: 'history', params: { siteId: siteId.value } })
}
</script>

<template>
  <div class="bell-menu" role="dialog" aria-label="알림 목록">
    <div class="head">알림<span>오늘</span></div>

    <p v-if="rows === null" class="noval">불러오는 중</p>
    <div v-else-if="failed && !items.length" class="noval">
      <AppIcon name="alert" :size="24" />
      <b>알림을 불러오지 못했습니다</b>
    </div>
    <div v-else-if="!items.length" class="noval">
      <AppIcon name="bell" :size="24" />
      <b>새 알림이 없습니다</b>
    </div>
    <div v-else class="list">
      <div v-for="r in items" :key="r.key" class="al" :class="r.active ? `active is-${r.level}` : ''">
        <span class="ti"><AppIcon :name="r.icon" :size="18" /></span>
        <div class="tx">
          <b>{{ r.title }}</b>
          <span>{{ r.place }}</span>
        </div>
        <div class="rt">
          <span class="chip" :class="r.chip.cls">{{ r.chip.text }}</span>
          <span class="mono num">{{ r.time }}</span>
        </div>
      </div>
    </div>

    <button v-if="siteId" type="button" class="pgo" @click="goHistory">
      알림 이력 전체 보기 <AppIcon name="cr" :size="14" />
    </button>
    <div v-else class="pgo no">사업장을 고르면 알림 이력을 볼 수 있습니다</div>
  </div>
</template>

<style scoped>
/* 설계안 .menu.wide: 너비 420, 그림자, 둥근 8px, 안쪽 여백 없음 */
.bell-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  z-index: 30;
  width: 420px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--color-border-card);
  border-radius: 8px;
  box-shadow:
    0 10px 30px rgba(15, 23, 42, 0.14),
    0 2px 6px rgba(15, 23, 42, 0.08);
  text-align: left;
  cursor: default;
}

.head {
  display: flex;
  align-items: center;
  padding: 14px 16px 10px;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
}

.head span {
  margin-left: auto;
  font-size: 12.5px;
  font-weight: 400;
  color: #94a3b8;
}

/* 알림 한 줄 (설계안 .al 을 높이 68 로) */
.al {
  position: relative;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid #f1f5f9;
}

.al:last-child {
  border-bottom: none;
}

/* 조치가 끝나지 않은 알림: 왼쪽 색 띠 깜빡임 + 옅은 배경 (설계안 .al.new) */
.al.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  animation: bell-bar 1.2s ease-in-out infinite;
}

.al.active.is-warn {
  background: #fffdf0;
}

.al.active.is-warn::before {
  background: var(--color-level-warn);
}

.al.active.is-alarm,
.al.active.is-fire {
  background: #fef6f6;
}

.al.active.is-alarm::before,
.al.active.is-fire::before {
  width: 5px;
  background: var(--color-level-alarm);
  animation-duration: 0.7s;
}

.ti {
  width: 34px;
  height: 34px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
}

.active.is-warn .ti {
  background: var(--color-level-warn-soft);
  color: #ea580c;
}

.active.is-alarm .ti,
.active.is-fire .ti {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.tx {
  flex: 1;
  min-width: 0;
}

.tx b {
  display: block;
  margin-bottom: 2px;
  font-size: 14px;
  color: var(--color-text);
}

.tx span {
  display: block;
  max-width: 230px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-sub);
}

.rt {
  text-align: right;
}

.num {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 진행 상태 칩 (오늘 알림 내역 카드와 같은 색) */
.chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
}

.chip.is-warn {
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
}

.chip.is-alarm,
.chip.is-fire {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.chip.is-info {
  background: #e0f2fe;
  color: #0369a1;
}

.chip.is-done {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

/* 빈 목록 · 불러오는 중 (설계안 .noval, 높이 150) */
.noval {
  height: 150px;
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.noval b {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-sub);
}

/* 아래 '알림 이력 전체 보기' (설계안 .pop .pgo) */
.pgo {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px;
  border: none;
  border-top: 1px solid #e2e8f0;
  background: none;
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms;
}

.pgo:hover {
  background: var(--color-primary-soft, #eff6ff);
}

.pgo.no {
  color: var(--color-text-weak);
  font-weight: 500;
  cursor: default;
}

.pgo.no:hover {
  background: none;
}

@keyframes bell-bar {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

@media (prefers-reduced-motion: reduce) {
  .al.active::before {
    animation: none;
  }
}
</style>
