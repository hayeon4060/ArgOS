<!--
  체크리스트 항목 진행 보기 (설계안 mChecklist() 오른쪽, 화면설계서 C3 ③④) — PC 에서는 읽기만
  - 끝난 항목: 초록 바탕 + 체크(튀며 나타남 .35초) + 완료 시각
  - 지금 하는 항목: 파란 테두리가 숨쉬듯(1.6초) + '진행 중'
  - 끝난 항목 결과가 '이상 없음'이 아니면(조치함 · 부품 교체 예정 · 추가 점검 필요) 주황 글자로
  - 아래: 현장 메모 (담당자 앱에서 항목마다 남긴 메모 + 완료 보고 한 줄)
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** GET /checklists/{id} 의 items: [{ id, seq, content, doneAt, result, memo }] */
  items: { type: Array, required: true },
  /** 완료 보고 때 남긴 한 줄 (없으면 빈 값) */
  reportMemo: { type: String, default: '' },
})

const current = computed(() => props.items.findIndex((i) => !i.doneAt))
const memos = computed(() => props.items.filter((i) => i.memo))
const hm = (iso) => iso?.slice(11, 19) ?? ''
</script>

<template>
  <div>
    <div v-for="(item, i) in items" :key="item.id" class="ck" :class="{ dn: item.doneAt, cur: i === current }">
      <span class="bx"><AppIcon v-if="item.doneAt" name="ok" :size="14" /></span>
      <span class="text">{{ item.content }}</span>
      <span v-if="item.doneAt && item.result && item.result !== '이상 없음'" class="res">{{ item.result }}</span>
      <span class="tm mono">{{ item.doneAt ? hm(item.doneAt) : i === current ? '진행 중' : '' }}</span>
    </div>

    <div class="memo">
      <b>현장 메모</b>
      <p v-for="m in memos" :key="m.id">
        <span class="mono at">{{ m.seq }}번 · {{ hm(m.doneAt) }}</span><br />{{ m.memo }}
      </p>
      <p v-if="reportMemo"><span class="mono at">완료 보고</span><br />{{ reportMemo }}</p>
      <template v-if="!memos.length && !reportMemo"><br /><span class="none">아직 남긴 메모가 없습니다.</span></template>
    </div>
  </div>
</template>

<style scoped>
.ck {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 52px;
  margin-bottom: 8px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 14px;
  white-space: nowrap;
  /* 끝나는 순간 바탕이 연한 초록으로 300ms (화면설계서 C3) */
  transition:
    background 300ms,
    border-color 300ms,
    box-shadow 300ms;
}

.bx {
  width: 20px;
  height: 20px;
  flex: none;
  display: grid;
  place-items: center;
  border: 2px solid var(--color-border-strong);
  border-radius: var(--radius-xs);
  color: #fff;
  transition:
    background 250ms,
    border-color 250ms;
}

.bx .app-icon {
  stroke-width: 3;
  animation: check-pop 350ms cubic-bezier(0.2, 1.4, 0.3, 1) both;
}

.text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tm {
  font-size: 12px;
  color: var(--color-text-weak);
}

.res {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-level-warn-text);
}

.memo p {
  margin-top: 6px;
}

.ck.dn {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.ck.dn .bx {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal);
}

.ck.dn .text {
  color: var(--color-text-sub);
}

.ck.cur {
  border-color: #93c5fd;
  animation: cur-pulse 1.6s ease-in-out infinite;
}

.ck.cur .tm {
  color: var(--color-primary);
  font-family: var(--font-sans);
  font-weight: 600;
}

.memo {
  min-height: 62px;
  margin-top: 6px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: #f8fafc;
  font-size: 12.5px;
  line-height: 19px;
  color: var(--color-text-sub);
  /* 새 메모가 생기면 아래에서 올라옴 200ms */
  animation: rise 200ms ease-out both;
}

.memo b {
  color: var(--color-text);
}

.memo .at {
  margin-left: 4px;
  color: var(--color-text-weak);
}

.memo .none {
  color: var(--color-text-weak);
}

@keyframes check-pop {
  from {
    transform: scale(0);
  }
  to {
    transform: scale(1);
  }
}

@keyframes cur-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(37, 99, 235, 0.22);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ck.cur {
    animation: none;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
  }
}
</style>
