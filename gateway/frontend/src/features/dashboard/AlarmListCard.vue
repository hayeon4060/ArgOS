<!--
  오늘 알림 내역 카드: 오늘 0시부터 지금까지 난 알림 전부, 최근 순 (GET /alarms?date=today)
  - 2026-11-19 재원님 요청: 이름 '실시간 알림' → '오늘 알림 내역', 표시 기준 = 당일 하루 누적
  - 제목줄: 제목 옆 '과거 알림' 단추(누르면 통계·이력 관리 화면으로 이동) + 오른쪽 오늘 누적·미조치 건수
    ('누르면 조치 내역' 설명 글자는 삭제. 줄을 누르면 펼쳐지는 동작은 그대로)
  - 줄을 누르면 그 줄만 펼쳐 조치 내역 (AlarmRow), 다시 누르면 접힘
  - 펼친 줄의 단추: 진행 중이면 알림 창(주의 상세 / 체크리스트 진행), 끝났으면 통계·이력 관리
  - 새로 생긴 알림 줄은 위에서 밀려 들어오며 2초 강조 (처음 화면을 열 때는 강조 안 함)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AlarmRow from './AlarmRow.vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import { useAlertStore } from '@/stores/alert'
import { toast } from '@/utils/toast'

const props = defineProps({
  alarms: { type: Array, required: true },
})

const route = useRoute()
const router = useRouter()
const alert = useAlertStore()

const openCount = computed(() => props.alarms.filter((a) => a.status !== 'CLOSED').length)

// 펼친 줄 (하나만)
const openId = ref(null)
function toggle(id) {
  openId.value = openId.value === id ? null : id
}

// 새로 생긴 줄 찾기: 지난번 목록에 없던 알림 id
const freshIds = ref(new Set())
let seen = null
watch(
  () => props.alarms,
  (list) => {
    const ids = list.map((a) => a.id)
    freshIds.value = seen ? new Set(ids.filter((id) => !seen.has(id))) : new Set()
    seen = new Set(ids)
  },
  { immediate: true },
)

/** 진행 중 알림 → 알맞은 창 (본사 관리자가 조치 전 알림을 누르면 안내만) */
function onAct(alarm) {
  if (!alert.openFor(alarm)) toast('주의 확인과 조치는 공장 담당자 화면에서 합니다.', { type: 'info' })
}

/**
 * '과거 알림' 단추 → 통계·이력 관리 화면 (어제 이전 알림은 그 화면에서 기간을 골라 봄)
 * - 2026-11-19: 누른 화면의 범위가 미리 골라진 채로 열림 (주소 ?factoryId=&processId=, HistoryPage 가 읽음)
 *   공정 화면 → 그 공장·공정 / 공장 대시보드 → 그 공장 / 사업장 현황 → 전체 공장(factoryId=all)
 */
function goHistory() {
  const { siteId, factoryId, processId } = route.params
  const query = processId ? { factoryId, processId } : factoryId ? { factoryId } : { factoryId: 'all' }
  router.push({ name: 'history', params: { siteId }, query })
}

/** 끝난 알림 → 통계·이력 관리 (이 알림 조건) */
function onHistory(alarm) {
  router.push({ name: 'history', params: { siteId: route.params.siteId }, query: { alarmId: alarm.id } })
}
</script>

<template>
  <BaseCard title="오늘 알림 내역">
    <!-- 제목 옆: 과거 알림 단추 -->
    <template #sub>
      <button type="button" class="past" @click="goHistory">
        <AppIcon name="clock" :size="14" />과거 알림<AppIcon name="cr" :size="14" />
      </button>
    </template>
    <!-- 오른쪽: 오늘 누적 건수 · 미조치(조치 완료 전) 건수 -->
    <template #actions>
      <span>누적 <b class="mono">{{ alarms.length }}</b>건</span>
      <span class="bar" />
      <span>미조치 <b class="mono" :class="{ open: openCount > 0 }">{{ openCount }}</b>건</span>
    </template>

    <div class="list">
      <AlarmRow
        v-for="alarm in alarms"
        :key="alarm.id"
        :alarm="alarm"
        :open="openId === alarm.id"
        :fresh="freshIds.has(alarm.id)"
        @toggle="toggle(alarm.id)"
        @act="onAct"
        @history="onHistory"
      />
      <p v-if="alarms.length === 0" class="empty">오늘 알림이 없습니다.</p>
    </div>
  </BaseCard>
</template>

<style scoped>
b {
  font-size: 15px;
  color: var(--color-text);
}

b.open {
  color: var(--color-level-alarm-text);
}

/* 누적 | 미조치 사이 세로 선 */
.bar {
  width: 1px;
  height: 12px;
  background: var(--color-border-strong);
}

/* 과거 알림 단추: 테두리만 있는 작은 단추, 올리면 파란 글자·옅은 파란 바탕 */
.past {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 6px 0 9px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-sub);
  cursor: pointer;
  transition:
    color var(--m-hover),
    border-color var(--m-hover),
    background-color var(--m-hover);
}

.past:hover,
.past:focus-visible {
  border-color: #93c5fd;
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

/* 4줄(84px × 4)이 넘으면 카드 안에서 스크롤 (설계안 카드 높이와 같게) */
.list {
  max-height: 336px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.empty {
  padding: 40px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
