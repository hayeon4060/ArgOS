<!--
  알림 대응 창·알림 띠·화면 테두리를 그리는 곳 (MainLayout 에 한 번)
  - body 로 통째로 옮겨 그림(Teleport): 화면 전환 확대·축소, 본문 잘림(overflow)에 영향 안 받게
  - 열린 창: stores/alert.js 의 modal (주의 알림 → 주의 상세(센서 데이터·열화상 포함) / 체크리스트 진행)
  - 하는 일
    · 처음 + 데이터가 바뀔 때마다 대응 중인 알림·체크리스트를 다시 받음 (useLiveReload)
    · 새 알림이 생기면 (화재 > 경보 > 주의 중 높은 것 하나)
      같은 설비에 경보·화재가 진행 중이면 그 설비의 주의는 새 알림으로 안 봄 (stores/alert.js visibleAlarms, 2026-11-22 2중 알림)
      → 경보가 끝난 뒤에도 그 주의가 '발생 중'이면 그때 주의 알림 창이 뜸
      공장 담당자: 주의 알림 창 / 경보·화재 대응 창을 바로 띄움 (경보·화재는 열려 있던 창 위로)
      본사 관리자: 알림 띠 ('위치 보기', 화재는 '화재 현황 보기'로 닫을 때까지)
    · 담당자가 체크리스트를 모두 마치면: 알림 띠 '확인하기' → 체크리스트 진행 창
    · 대응 중 알림이 있으면 화면 가장자리 테두리가 숨쉬듯 (설계안 #vig: 주의 주황 1.6초, 경보 빨강 0.8초, 화재 0.6초)
      탭 제목 앞에 [주의] / [경보] / [화재]
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import AlarmResponseModal from './AlarmResponseModal.vue'
import { placeOf, siteNameOf } from './alarmPlace'
import { hazardLabelOf } from './alarmView'
import ChecklistProgressModal from './ChecklistProgressModal.vue'
import WarnDetailModal from './WarnDetailModal.vue'
import WarnNoticeModal from './WarnNoticeModal.vue'
import AppToasts from '@/components/AppToasts.vue'
import { useAlertStore } from '@/stores/alert'
import { LEVEL, LEVEL_META } from '@/types/codes'
import { useLiveReload } from '@/utils/useLiveReload'
import { toast } from '@/utils/toast'

const router = useRouter()
const alert = useAlertStore()

onMounted(alert.refresh)
useLiveReload(alert.refresh)

/** 열린 창에 넘길 알림 (주의 알림 창은 알림 한 건 전체가 필요) */
const modalAlarm = computed(() => alert.activeAlarms.find((a) => a.id === alert.modal?.alarmId) ?? null)

/* ───────── 새 알림 · 체크리스트 완료 알아채기 ───────── */
let known = null // 지난번 받은 { 알림 id: 상태 } (처음 한 번은 '모두 새것'으로 보고 창만 띄움, 알림 띠는 안 띄움)
let reportedIds = new Set()

const isEmergency = (a) => a.level === LEVEL.ALARM || a.level === LEVEL.FIRE
/** '울산공장 5공장 도장 공정' (사업장 이름은 siteId 로 받음 — 알림 location 은 공장부터) */
async function where(a) {
  const p = placeOf(a)
  return [await siteNameOf(a.siteId), p.factoryName, p.processName].filter(Boolean).join(' ')
}
const goProcess = (a) => router.push({ name: 'process', params: { siteId: a.siteId, factoryId: a.factoryId, processId: a.processId } })

/** 화재 알림 띠 닫기 함수 (화재가 해제되면 같이 닫음) */
let closeFireToast = null

/** 본사 관리자 알림 띠 (설계안 onState() hq: 주의 · 경보 · 화재) */
async function notifyHq(a) {
  const at = await where(a)
  // 유형 문구 '가스 ' · '화재 ' (창 문구와 같게)
  const kind = hazardLabelOf(a) ? `${hazardLabelOf(a)} ` : ''
  if (a.level === LEVEL.FIRE) {
    const site = [await siteNameOf(a.siteId), placeOf(a).factoryName].filter(Boolean).join(' ')
    closeFireToast = toast(`${site}에서 화재가 감지되었습니다.`, {
      type: 'err',
      ms: 0, // 화재는 직접 닫을 때까지
      action: '화재 현황 보기',
      onAction: () => router.push({ name: 'site-select' }),
    })
  } else if (a.level === LEVEL.ALARM) {
    toast(`${at} ${placeOf(a).equipmentName}에서 ${kind}경보가 발생했습니다.`, { type: 'err', ms: 6000, action: '위치 보기', onAction: () => goProcess(a) })
  } else {
    toast(`${at}에서 ${kind}주의가 발생했습니다.`, { type: 'warn', action: '위치 보기', onAction: () => goProcess(a) })
  }
}

/**
 * 열려 있는 주의 창이 지금 알림 상태와 안 맞으면 정리 (2026-11-19 전수조사)
 * - 주의 알림 창(길게 눌러 확인): 그 알림이 더는 '발생 중'이 아니면 닫음. 다른 사람이 먼저 확인했으면 주의 상세로 넘어감
 *   (닫지 않으면 잠긴 창이 다른 알림 내용으로 바뀌어 남음 — 예: 가스 주의 창이 경보 직전에 '화재 주의'로 바뀜)
 * - 주의 상세 창: 그 알림이 대응 목록에서 빠졌으면(종료 · 초기화) 닫음
 */
function closeStale(list) {
  const m = alert.modal
  if (!m) return
  const a = list.find((x) => x.id === m.alarmId)
  if (m.type === 'warn' && a?.status !== 'OPEN') {
    if (a?.status === 'ACKED' && alert.canAct) alert.open('detail', { alarmId: a.id })
    else alert.close()
  } else if (m.type === 'detail' && !a) {
    alert.close()
  }
}

watch(
  () => alert.visibleAlarms,
  (list) => {
    const first = !known
    const before = known ?? {}
    known = Object.fromEntries(list.map((a) => [a.id, a.status]))
    // 화재가 끝났으면 '닫을 때까지' 띄워 둔 화재 알림 띠도 닫음
    if (closeFireToast && !list.some((a) => a.level === LEVEL.FIRE)) {
      closeFireToast()
      closeFireToast = null
    }
    closeStale(alert.activeAlarms) // 창 정리는 숨긴 주의까지 포함한 전체 목록으로
    // 새로 '발생 중(OPEN)'이 된 알림 (처음이면 지금 OPEN 인 것 전부)
    const fresh = list.filter((a) => a.status === 'OPEN' && before[a.id] !== 'OPEN')
    if (!fresh.length) return

    // 높은 단계 하나만 처리: 화재 > 경보 > 주의 (설계안: 화재 창이 경보 창 위를 덮음)
    const fire = fresh.find((a) => a.level === LEVEL.FIRE)
    const top = fire ?? fresh.find(isEmergency) ?? fresh[0]

    if (alert.canAct) {
      // 경보·화재는 열린 창이 있어도 바로 바꿔 띄움, 주의는 다른 창이 없을 때만 (설계안: 0.65초 뒤)
      if (isEmergency(top)) setTimeout(() => alert.openFor(top), 650)
      else if (!alert.modal) setTimeout(() => alert.modal || alert.open('warn', { alarmId: top.id }), 650)
    } else if (!first) {
      notifyHq(top)
    }
  },
)

watch(
  () => alert.checklists,
  (list) => {
    const done = list.filter((c) => c.status === 'REPORTED')
    // 경보·화재 대응 중에는 '확인하기' 알림 띠를 미룸 (누르면 잠긴 경보·화재 창이 체크리스트 창으로 바뀌어 버림 — 2026-11-19 전수조사)
    // 경보·화재가 끝난 뒤 다시 받을 때 띄움
    const emergency = alert.activeAlarms.some(isEmergency)
    for (const c of done) {
      if (reportedIds.has(c.id) || emergency) continue
      reportedIds.add(c.id)
      if (alert.canAct && alert.modal?.type !== 'checklist') {
        toast(`${c.assignee.name} 님이 체크리스트 ${c.totalCount}개 항목을 모두 마쳤습니다.`, {
          ms: 6000,
          action: '확인하기',
          onAction: () => alert.open('checklist', { alarmId: c.alarmId, checklistId: c.id }),
        })
      }
    }
    reportedIds = new Set([...reportedIds].filter((id) => list.some((c) => c.id === id)))
  },
)

/* ───────── 화면 테두리 · 탭 제목 ───────── */
const level = computed(() => alert.primary?.level ?? null)
const vignette = computed(() => (level.value ? LEVEL_META[level.value]?.key : ''))

watch(
  level,
  (lv) => {
    const prefix = lv ? `[${LEVEL_META[lv]?.label}] ` : ''
    document.title = `${prefix}ArgOS 환경안전 통합관제`
  },
  { immediate: true },
)
// 로그아웃 등으로 이 화면들을 떠나면 제목을 원래대로, 열린 창·받아 둔 알림도 비움 (다른 계정으로 다시 들어올 때 남지 않게)
onBeforeUnmount(() => {
  document.title = 'ArgOS 환경안전 통합관제'
  alert.reset()
})
</script>

<template>
  <Teleport to="body">
    <!-- 화면 가장자리 상태 테두리 (누르기는 통과) -->
    <div v-if="vignette" class="vignette" :class="vignette" aria-hidden="true" />

    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <WarnNoticeModal v-if="alert.modal?.type === 'warn' && modalAlarm" :key="`warn-${alert.modal.alarmId}`" :alarm="modalAlarm" />
      <WarnDetailModal v-else-if="alert.modal?.type === 'detail'" :key="`detail-${alert.modal.alarmId}`" :alarm-id="alert.modal.alarmId" />
      <AlarmResponseModal v-else-if="alert.modal?.type === 'alarm'" :key="`alarm-${alert.modal.alarmId}`" :alarm-id="alert.modal.alarmId" />
      <ChecklistProgressModal
        v-else-if="alert.modal?.type === 'checklist'"
        :key="`ck-${alert.modal.checklistId}`"
        :checklist-id="alert.modal.checklistId"
      />
    </Transition>

    <AppToasts />
  </Teleport>
</template>

<style scoped>
/* 화면 가장자리 테두리 (설계안 #vig.warn: 안쪽 5px 주황, 1.6초 숨쉬기)
   - keyframes 이름이 blink- 로 시작 → 지도·카드 점멸과 같은 박자 (utils/blink.js) */
.vignette {
  position: fixed;
  inset: 0;
  z-index: 90;
  pointer-events: none;
}

.vignette.warn {
  box-shadow: inset 0 0 0 5px rgba(249, 115, 22, 0.95);
  animation: blink-vig var(--m-pulse-warn) ease-in-out infinite;
}

.vignette.alarm {
  box-shadow:
    inset 0 0 0 7px var(--color-level-alarm),
    inset 0 0 90px rgba(220, 38, 38, 0.28);
  animation: blink-vig var(--m-pulse-alarm) ease-in-out infinite;
}

/* 화재: 더 굵고 진하게, 0.6초 (2026-11-20 다른 화재 점멸과 같은 --m-pulse-fire 로 맞춤) */
.vignette.fire {
  box-shadow:
    inset 0 0 0 9px #b91c1c,
    inset 0 0 160px rgba(220, 38, 38, 0.42);
  animation: blink-vig var(--m-pulse-fire) ease-in-out infinite;
}

@keyframes blink-vig {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

/* 움직임 줄이기: 깜빡임 대신 굵은 테두리 */
@media (prefers-reduced-motion: reduce) {
  .vignette {
    animation: none !important;
  }
}
</style>
