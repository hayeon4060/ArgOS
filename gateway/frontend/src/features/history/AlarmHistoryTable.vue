<!--
  알림 이력 표 (2026-11-19 개편: 왼쪽 열 전체 높이)
  - 머리줄: 유형 거르기(전체 / 화재 / 가스) + 상태 탭(전체 / 조치 중 / 해제), 한 쪽 14행, '89건 중 1~14' + 쪽 번호
  - 열: 발생 시각, 유형, 공정·설비, 등급, 조치, 조치 시간(막대), 상태, 상세 (센서·측정값 열은 뺌 → 상세 패널에서 봄)
  - 상세 단추(또는 줄)를 누르면 open(row) → 화면 오른쪽에 상세 패널. 열린 줄은 파란 바탕 + 왼쪽 선
  - 쪽을 넘기면 표 내용이 0.15초 흐려졌다 돌아옴
-->
<script setup>
import { computed } from 'vue'
import { HAZARDS, HAZARD_META, actionText, hazardOf, isActive, minutesOf, placeOf, shortTime } from './historyFilter'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import SegButtons from '@/components/SegButtons.vue'

const props = defineProps({
  /** GET /stats/alarms 의 items (불러오기 전이면 null) */
  rows: { type: Array, default: null },
  total: { type: Number, default: 0 },
  perPage: { type: Number, default: 6 },
  busy: { type: Boolean, default: false },
  /** 응답의 서버 시각 (있으면 조치 중 '경과' 분을 이걸로 셈) */
  serverTime: { type: String, default: '' },
  /** 상세 패널이 열린 알림 id (없으면 null) */
  selectedId: { type: Number, default: null },
})

const emit = defineEmits(['open'])
/** 탭: '' 전체 / 'ACTIVE' 조치 중 / 'CLOSED' 해제 */
const tab = defineModel('tab', { type: String, default: '' })
/** 유형: '' 전체 / 'FIRE' 화재 / 'GAS' 가스 */
const hazard = defineModel('hazard', { type: String, default: '' })
/** 쪽 (1부터) */
const page = defineModel('page', { type: Number, default: 1 })

const TABS = [
  { value: '', label: '전체' },
  { value: 'ACTIVE', label: '조치 중' },
  { value: 'CLOSED', label: '해제' },
]

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.perPage)))
/** 쪽 번호 (5쪽 넘으면 … 로 줄임 — 설계안과 같음) */
const pageList = computed(() => {
  const n = pages.value
  const p = page.value
  if (n <= 5) return Array.from({ length: n }, (_, i) => i + 1)
  if (p <= 3) return [1, 2, 3, '…', n]
  if (p >= n - 2) return [1, '…', n - 2, n - 1, n]
  return [1, '…', p, '…', n]
})
const from = computed(() => (page.value - 1) * props.perPage + 1)
const to = computed(() => Math.min(props.total, page.value * props.perPage))

const view = computed(() => {
  // 조치 중인 건의 '경과' 분 (다시 불러올 때마다 새로 셈, 서버 시각이 있으면 그걸로)
  const now = props.serverTime ? new Date(props.serverTime).getTime() : Date.now()
  return (props.rows ?? []).map((r) => {
    const place = placeOf(r.location)
    const active = isActive(r)
    const minutes = minutesOf(r, now)
    return {
      row: r,
      key: r.id,
      time: shortTime(r.raisedAt),
      hazard: HAZARD_META[hazardOf(r)],
      equipment: place.equipment,
      process: place.process,
      active,
      minutes,
      action: actionText(r),
      // 조치 시간 막대: 30분이면 가득
      bar: Math.min(100, (minutes / 30) * 100),
    }
  })
})
</script>

<template>
  <BaseCard title="알림 이력" :sub="`${total}건`" class="history-card">
    <template #actions>
      <SegButtons v-model="hazard" :options="HAZARDS" small @update:model-value="page = 1" />
      <SegButtons v-model="tab" :options="TABS" small @update:model-value="page = 1" />
    </template>

    <div class="body" :class="{ busy }" :style="{ '--rows': perPage }">
      <table v-if="view.length" class="tb">
        <colgroup>
          <col class="c-time" /><col class="c-hz" /><col /><col class="c-lv" /><col class="c-act" /><col class="c-dur" /><col class="c-state" /><col class="c-more" />
        </colgroup>
        <thead>
          <tr>
            <th>발생 시각</th>
            <th>유형</th>
            <th>공정 · 설비</th>
            <th>등급</th>
            <th>조치</th>
            <th>조치 시간</th>
            <th>상태</th>
            <th class="t-more">상세</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="v in view"
            :key="v.key"
            class="row"
            :class="{ sel: v.key === selectedId }"
            @click="emit('open', v.row)"
          >
            <td class="mono t-time">{{ v.time }}</td>
            <td>
              <span class="hz" :class="v.row.hazard"><AppIcon :name="v.hazard.icon" :size="14" :style="{ color: v.hazard.color }" />{{ v.hazard.label }}</span>
            </td>
            <td class="t-place"><b>{{ v.equipment }}</b> <span class="sub">{{ v.process }}</span></td>
            <td><LevelBadge :level="v.row.level" /></td>
            <td>{{ v.action }}</td>
            <td>
              <span class="dur">
                <i><u :class="{ on: v.active }" :style="{ width: `${v.bar}%` }" /></i>
                <span class="mono">{{ v.minutes }}분{{ v.active ? ' 경과' : '' }}</span>
              </span>
            </td>
            <td>
              <span class="dotc" :class="{ on: v.active }"><i />{{ v.active ? '조치 중' : '해제' }}</span>
            </td>
            <td class="t-more">
              <!-- 줄 누르기와 같은 동작 (단추를 눌러도 줄 클릭이 한 번만 일어나게 .stop) -->
              <button type="button" class="more" :aria-label="`${v.equipment} 알림 상세`" @click.stop="emit('open', v.row)">
                상세<AppIcon name="cr" :size="14" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="rows" class="noval">
        <AppIcon name="bell" :size="24" />
        <b>{{ tab === 'ACTIVE' ? '조치 중인 알림이 없습니다' : '조건에 맞는 알림이 없습니다' }}</b>
        <template v-if="tab !== 'ACTIVE'">기간이나 조건을 바꿔 보세요.</template>
      </div>
      <div v-else class="noval">불러오는 중</div>
    </div>

    <div v-if="view.length" class="pg">
      <span class="count">{{ total }}건 중 {{ from }}~{{ to }}</span>
      <template v-for="(p, i) in pageList" :key="`${p}-${i}`">
        <span v-if="p === '…'" class="dots">…</span>
        <button v-else type="button" class="mono" :class="{ on: p === page }" :aria-current="p === page ? 'page' : undefined" @click="page = p">
          {{ p }}
        </button>
      </template>
    </div>
  </BaseCard>
</template>

<style scoped>
/* 카드가 왼쪽 열 높이를 다 채우고, 쪽 번호 줄은 맨 아래에 붙음 */
.history-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.body {
  flex: 1;
  /*
   * 표 높이는 카드에 남은 높이를 그대로 씀 (페이지가 화면 높이에 맞춰 카드 높이를 정함)
   * - container-type: size → 아래 td 가 이 칸 높이(100cqh)를 기준으로 줄 높이를 계산할 수 있게
   * - 화면이 아주 낮아 14행이 안 들어가면 이 칸 안에서만 스크롤
   */
  min-height: 0;
  overflow-y: auto;
  container-type: size;
  transition: opacity 150ms;
}

.body.busy {
  opacity: 0.4;
}

.tb {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13.5px;
}

/* 열 너비 (공정·설비 열이 남는 폭을 가짐) */
.c-time { width: 128px; }
.c-hz { width: 92px; }
.c-lv { width: 92px; }
.c-act { width: 140px; }
.c-dur { width: 160px; }
.c-state { width: 100px; }
.c-more { width: 88px; }

th {
  height: 40px;
  padding: 0 12px;
  background: var(--color-surface-sub);
  border-bottom: 1px solid var(--color-border);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-sub);
  text-align: left;
  white-space: nowrap;
}

td {
  /*
   * 줄 높이 = (표 칸 높이 − 머리줄 41px) ÷ 한 쪽 행 수(--rows, 14) — 화면 높이에 맞춰 14행이 딱 들어가게
   * 32px 보다 낮아지지 않고(글자·단추가 눌리지 않게) 46px(설계 값)보다 높아지지 않음
   * 마지막 쪽처럼 행이 적어도 줄 높이는 같음
   */
  height: clamp(32px, calc((100cqh - 41px) / var(--rows, 14)), 46px);
  padding: 0 12px;
  border-bottom: 1px solid #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.t-more {
  padding-right: 16px;
  text-align: right;
}

th:first-child,
td:first-child {
  padding-left: 20px;
}

.t-time {
  font-size: 13px;
}

.row {
  cursor: pointer;
  transition: background var(--m-hover);
}

.row:hover {
  background: var(--color-surface-sub);
}

/* 상세 패널이 열린 줄 */
.row.sel {
  background: var(--color-primary-soft);
}

.row.sel td:first-child {
  box-shadow: inset 3px 0 0 var(--color-primary);
}

/* 유형 칩: 회색 바탕 + 아이콘 (아이콘 색은 historyFilter.js HAZARD_META) */
.hz {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 9px 0 7px;
  border-radius: 12px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12.5px;
  font-weight: 600;
}

/* 상세 단추 */
.more {
  height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0 8px 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-sub);
  cursor: pointer;
  transition: color var(--m-hover), border-color var(--m-hover);
}

.more:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.row.sel .more {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

b {
  font-weight: 600;
}

.sub {
  font-size: 12px;
  color: var(--color-text-weak);
}

.dur {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dur i {
  display: block;
  width: 90px;
  height: 6px;
  border-radius: 2px;
  background: #eef2f6;
  overflow: hidden;
}

.dur u {
  display: block;
  height: 100%;
  background: #64748b;
  transition: width var(--m-value);
}

.dur u.on {
  background: var(--color-level-warn);
}

.dur .mono {
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.dotc {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.dotc i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-weak);
}

.dotc.on {
  color: var(--color-level-warn-text);
}

.dotc.on i {
  background: var(--color-level-warn);
}

.noval {
  height: 684px;
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
  color: var(--color-text-sub);
}

.pg {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  padding: 0 16px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.pg .count {
  margin-right: 8px;
}

.pg button,
.pg .dots {
  min-width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--color-primary);
}

.pg button {
  cursor: pointer;
}

.pg button.on {
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-weight: 600;
}

.pg .dots {
  color: var(--color-text-weak);
}
</style>
