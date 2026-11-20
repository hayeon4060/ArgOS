<!--
  오늘 알림 내역 한 줄 (설계안 css/base.css .al, v-rev.js rightCol() .al2)
  - 왼쪽: 센서 종류 아이콘 (진행 중이면 상태 색, 끝났으면 회색)
  - 가운데: 제목, 위치 · 센서 태그 · 값, 누가 무엇을 하는 중인지 한 줄 ('조치 중 · 김도현 (2/5)')
  - 오른쪽: 진행 상태 칩 + 발생 시각, 펼침 꺾쇠
  - 진행 중(조치 완료 전)인 알림은 왼쪽 색 띠(1.2초 깜빡) + 옅은 배경
  - 누르면 조치 내역이 0.24초에 펼쳐짐 (GET /alarms/{id}) + '주의 상세 열기' 또는 '이력에서 보기'
  - fresh: 방금 새로 생긴 줄 → 위에서 밀려 들어오고(0.25초) 바탕이 2초에 걸쳐 원래대로 (화면설계서 R2 --m-list-in)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import AlarmSteps from './AlarmSteps.vue'
import { getAlarmTimeline } from '@/api/alarms'
import AppIcon from '@/components/AppIcon.vue'
import { alarmChipOf, alarmIconOf, alarmTimeOf, isActiveAlarm, levelKeyOf } from '@/features/alert/alarmView'

const props = defineProps({
  /** GET /alarms 의 items 하나 */
  alarm: { type: Object, required: true },
  /** 펼쳤는지 (카드가 한 줄만 펼치게 관리) */
  open: { type: Boolean, default: false },
  /** 방금 새로 생긴 줄 */
  fresh: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle', 'act', 'history'])

// 아이콘 · 색 · 진행 상태 칩은 상단 알림 종 목록과 같은 규칙 (features/alert/alarmView.js)
const active = computed(() => isActiveAlarm(props.alarm))
const levelKey = computed(() => levelKeyOf(props.alarm))
const icon = computed(() => alarmIconOf(props.alarm))
const c = computed(() => props.alarm.checklist)
const chip = computed(() => alarmChipOf(props.alarm))

/** 누가 무엇을 하는 중인지 (설계안 actSteps().txt) */
const actor = computed(() => {
  const { status, assignee, level } = props.alarm
  if (level === 'FIRE') return status === 'OPEN' ? '경고 방송 송출 · 자동 셧다운 · 해제 대기' : '관리자 비밀번호 확인 후 해제'
  if (level === 'ALARM') return status === 'OPEN' ? '공장 담당자 조치 대기 · 셧다운 전' : '설비 셧다운 후 경보 종료'
  if (status === 'OPEN') return '공장 담당자 확인 전'
  if (status === 'ACKED') return '확인함 · 체크리스트 전송 전'
  if (status === 'ACTING' && c.value) {
    return c.value.status === 'REPORTED'
      ? `${assignee} 조치 완료 · 확인 대기`
      : `조치 중 · ${assignee} (${c.value.doneCount}/${c.value.totalCount})`
  }
  return '체크리스트 조치 후 해제'
})

const time = computed(() => alarmTimeOf(props.alarm))

/* ───────── 펼치면 조치 내역 불러오기 (열 때마다, 상태가 바뀌면 다시) ───────── */
const timeline = ref(null)
async function loadTimeline() {
  try {
    timeline.value = (await getAlarmTimeline(props.alarm.id)).timeline
  } catch {
    timeline.value = []
  }
}
watch(
  () => [props.open, props.alarm.status, c.value?.doneCount, c.value?.status],
  () => props.open && loadTimeline(),
  { immediate: true },
)
</script>

<template>
  <div class="alarm-row-wrap" :class="{ open, fresh }">
    <button type="button" class="alarm-row" :class="active ? `active is-${levelKey} blink-row blink-${levelKey}` : ''" :aria-expanded="open" @click="emit('toggle')">
      <span class="icon"><AppIcon :name="icon" :size="21" /></span>

      <div class="text">
        <b>{{ alarm.title }}</b>
        <span>
          {{ alarm.location }} <span class="mono">{{ alarm.sensorTag }}</span> ·
          <b class="mono">{{ alarm.value }}</b> {{ alarm.unit }}
        </span>
        <em><AppIcon name="user" :size="12" />{{ actor }}</em>
      </div>

      <div class="right">
        <span class="chip" :class="chip.cls">{{ chip.text }}</span>
        <span class="mono time">{{ time }}</span>
      </div>
      <AppIcon name="cd" :size="16" class="cv" />
    </button>

    <!-- 조치 내역: 높이 0 → 92px 로 0.24초 (grid 0fr → 1fr) -->
    <div class="ax">
      <div class="ax-inner">
        <div class="ax-body">
          <AlarmSteps v-if="timeline" :alarm="alarm" :timeline="timeline" />
          <button v-if="active" type="button" class="btn btn-primary small" @click="emit('act', alarm)">
            {{
              alarm.level === 'FIRE'
                ? '화재 창 열기'
                : alarm.level === 'ALARM'
                  ? '경보 창 열기'
                  : alarm.status === 'ACTING'
                    ? '체크리스트 진행 보기'
                    : '주의 상세 열기'
            }}
          </button>
          <button v-else type="button" class="btn small" @click="emit('history', alarm)">이력에서 보기</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.alarm-row-wrap {
  border-bottom: 1px solid #f1f5f9;
}

.alarm-row-wrap:last-child {
  border-bottom: none;
}

.alarm-row {
  position: relative;
  width: 100%;
  height: 84px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 40px 0 20px;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background 150ms;
}

.alarm-row:hover {
  background: #f8fafc;
}

/*
 * 진행 중인 알림: 왼쪽 색 띠(고정) + 줄 바탕이 진해졌다 옅어짐 (2026-11-20 재원님 선택, blink.css blink-row)
 * - 반복 시간 주의 1.6초 · 경보 0.8초 · 화재 0.6초
 */
.alarm-row.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}

.alarm-row.active.is-warn {
  background: #fffdf0;
}

.alarm-row.active.is-warn:hover {
  background: #fff7e6;
}

.alarm-row.active.is-warn::before {
  background: var(--color-level-warn);
}

.alarm-row.active.is-alarm,
.alarm-row.active.is-fire {
  background: #fef6f6;
}

.alarm-row.active.is-alarm::before,
.alarm-row.active.is-fire::before {
  width: 5px;
  background: var(--color-level-alarm);
}

/* 아이콘 칸: 기본은 회색(끝난 알림) */
.icon {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: #f1f5f9;
  color: #64748b;
}

.active.is-warn .icon {
  background: var(--color-level-warn-soft);
  color: #ea580c;
}

.active.is-alarm .icon,
.active.is-fire .icon {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.text {
  flex: 1;
  min-width: 0;
}

.text > b {
  display: block;
  margin-bottom: 2px;
  font-size: 15px;
}

.text > span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-text-sub);
}

.text em {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  font-size: 12px;
  font-style: normal;
  color: var(--color-text-weak);
  white-space: nowrap;
}

.right {
  text-align: right;
}

.time {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 펼침 꺾쇠: 펼치면 0.2초에 뒤집힘 */
.cv {
  position: absolute;
  right: 14px;
  top: 34px;
  color: var(--color-text-weak);
  transition: transform 200ms ease-out;
}

.open .cv {
  transform: rotate(180deg);
}

/* 진행 상태 칩 (LevelBadge 와 같은 모양, 아이콘 없음) */
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

/* 조치 내역 펼치기 (설계안 .al2 .ax: 높이 .24s) */
.ax {
  display: grid;
  grid-template-rows: 0fr;
  background: #f8fafc;
  transition: grid-template-rows 240ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.open .ax {
  grid-template-rows: 1fr;
  border-top: 1px solid #eef2f6;
}

.ax-inner {
  overflow: hidden;
}

.ax-body {
  height: 92px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 20px;
}

.btn.small {
  height: 34px;
  padding: 0 12px;
  font-size: 13px;
}

/* 새 줄: 위에서 밀려 들어오고 바탕이 2초에 걸쳐 원래대로 */
.fresh .alarm-row {
  animation:
    row-in 250ms ease-out both,
    row-flash 2s ease-out 250ms both;
}

@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(-12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes row-flash {
  from {
    background: var(--color-level-warn-soft);
  }
}


@media (prefers-reduced-motion: reduce) {
  .fresh .alarm-row {
    animation: none;
  }

  .ax,
  .cv {
    transition: none;
  }
}
</style>
