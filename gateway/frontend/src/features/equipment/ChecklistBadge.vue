<!--
  체크리스트 진행 배지 (설계안 css/base.css .ckb: 작은 고리 + '체크리스트 진행 2/5' + 담당자)
  - 설비에 진행 중 체크리스트가 있을 때만 제목 줄에 보임
  - 다 끝나면 초록색 + 1.6초마다 초록 빛번짐 (설계안 .ckb.done, donePulse)
  - 누르면 click → 체크리스트 진행 현황 창(C3) (상단 바 체크리스트 진행, 설비 상세 제목 줄이 같이 씀)
  - sub: 오른쪽 작은 글자 (기본 담당자 이름, 상단 바는 '도장 공정' / 다 끝나면 '완료 · 확인 필요')
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** { doneCount, totalCount, assignee } — assignee 는 이름 문자열 또는 { id, name } (백엔드 GET /equipment/{id} 의 activeChecklist) */
  checklist: { type: Object, required: true },
  sub: { type: String, default: '' },
})

defineEmits(['click'])

const done = computed(() => props.checklist.doneCount >= props.checklist.totalCount)
// 담당자 이름: 문자열이면 그대로, { id, name } 이면 name (객체를 그대로 쓰면 '[object Object]' 로 보임)
const assigneeName = computed(() => {
  const a = props.checklist.assignee
  return typeof a === 'string' ? a : (a?.name ?? '')
})
const subText = computed(() => (done.value ? '완료 · 확인 필요' : props.sub || assigneeName.value))

// 고리: 둘레 길이 중 완료 비율만큼 칠함
const R = 11
const LENGTH = 2 * Math.PI * R
const dash = computed(() => `${(LENGTH * props.checklist.doneCount) / props.checklist.totalCount} ${LENGTH}`)
</script>

<template>
  <button type="button" class="ckb" :class="{ done }" @click="$emit('click')">
    <svg width="28" height="28" viewBox="0 0 28 28" aria-hidden="true">
      <circle cx="14" cy="14" :r="R" fill="none" :stroke="done ? '#bbf7d0' : '#fed7aa'" stroke-width="4" />
      <circle
        cx="14"
        cy="14"
        :r="R"
        fill="none"
        class="bar"
        stroke-width="4"
        stroke-linecap="round"
        :stroke-dasharray="dash"
        transform="rotate(-90 14 14)"
      />
    </svg>
    <span class="lbl">체크리스트 진행</span>
    <span class="mono">{{ checklist.doneCount }}/{{ checklist.totalCount }}</span>
    <small>{{ subText }}</small>
  </button>
</template>

<style scoped>
.ckb {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 12px 0 8px;
  border: 1px solid #fdba74;
  border-radius: var(--radius-sm);
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition:
    box-shadow 200ms,
    background 300ms,
    border-color 300ms,
    color 300ms;
  /* 나타날 때 살짝 튀어 오름 (설계안 extra.css .ckb popin .4s) */
  animation: ckb-pop 400ms cubic-bezier(0.2, 1.2, 0.3, 1) both;
}

.ckb .bar {
  stroke: var(--color-level-warn);
  /* 진행 숫자가 바뀌면 고리가 부드럽게 참 (설계안 .6s) */
  transition: stroke-dasharray 600ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.ckb .mono {
  font-size: 14px;
}

.ckb small {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-level-warn-text);
}

/* 상단 바(compact): 좁은 화면에서 '도장 공정', 더 좁으면 '체크리스트 진행' 글자를 숨김 (고리와 2/5 는 남김) */
@media (max-width: 1600px) {
  .ckb.compact small {
    display: none;
  }
}

@media (max-width: 1440px) {
  .ckb.compact .lbl {
    display: none;
  }
}

.ckb:hover {
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.22);
}

.ckb.done {
  animation:
    ckb-pop 400ms cubic-bezier(0.2, 1.2, 0.3, 1) both,
    ckb-done 1.6s ease-in-out 400ms infinite;
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: var(--color-level-normal-text);
}

.ckb.done .bar {
  stroke: var(--color-level-normal);
}

.ckb.done small {
  color: var(--color-level-normal-text);
}

@media (prefers-reduced-motion: reduce) {
  .ckb {
    animation-name: softin; /* base.css: 투명도만 */
  }
}

@keyframes ckb-done {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(22, 163, 74, 0.4);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(22, 163, 74, 0);
  }
}

@keyframes ckb-pop {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
