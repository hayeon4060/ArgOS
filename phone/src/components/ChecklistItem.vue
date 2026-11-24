<!--
  체크리스트 항목 한 줄 (API 명세서 M2 PATCH /checklists/{id}/items/{itemId})
  - 줄을 누르면 펼쳐짐: 결과 고르기(이상 없음 · 조치함 · 부품 교체 예정 · 추가 점검 필요) + 메모 → '완료 체크'
  - 차례: 앞 항목부터 체크 (웹 진행 창도 '지금 하는 항목'을 차례로 보여 줌). 뒤 항목은 잠김
  - 이미 체크한 항목: 결과·메모를 고쳐 다시 저장, 마지막으로 체크한 항목은 '체크 풀기'
  - 끝낸 항목은 초록 바탕 + 체크, 결과가 '이상 없음'이 아니면 주황 글자
  - 저장은 부모(ChecklistPage)가 함: save({ done, result, memo })
-->
<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { ITEM_RESULTS } from '../codes'

const props = defineProps({
  /** { id, seq, content, doneAt, result, memo } */
  item: { type: Object, required: true },
  open: { type: Boolean, default: false },
  /** false 면 보기만 (완료 보고 뒤, 담당자가 바뀐 경우) */
  editable: { type: Boolean, default: true },
  /** 지금 체크할 차례인 항목 */
  next: { type: Boolean, default: false },
  /** 마지막으로 체크한 항목 (풀 수 있음) */
  last: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle', 'save'])

const result = ref(ITEM_RESULTS[0])
const memo = ref('')

/** 아직 차례가 아닌 항목 */
const locked = computed(() => !props.item.doneAt && !props.next)

// 펼칠 때마다 저장된 값으로 채움
watch(
  () => props.open,
  (open) => {
    if (!open) return
    result.value = props.item.result ?? ITEM_RESULTS[0]
    memo.value = props.item.memo ?? ''
  },
  { immediate: true },
)

const hm = (iso) => iso?.slice(11, 16) ?? ''
</script>

<template>
  <div class="item" :class="{ done: item.doneAt, open, locked: editable && locked }">
    <button type="button" class="line" :disabled="!editable || locked" :aria-expanded="open" @click="emit('toggle')">
      <span class="box"><AppIcon v-if="item.doneAt" name="ok" :size="16" /><span v-else class="mono">{{ item.seq }}</span></span>
      <span class="text">
        <span class="content">{{ item.content }}</span>
        <span v-if="item.doneAt" class="meta">
          <span class="mono">{{ hm(item.doneAt) }}</span>
          <span :class="{ warn: item.result && item.result !== ITEM_RESULTS[0] }">{{ item.result }}</span>
        </span>
        <span v-if="item.memo && !open" class="memo">{{ item.memo }}</span>
      </span>
      <AppIcon v-if="editable && !locked" :name="open ? 'cd' : 'cr'" :size="18" class="chev" />
      <AppIcon v-else-if="editable" name="lock" :size="16" class="chev" />
    </button>

    <div v-if="open && editable && !locked" class="editor">
      <span class="label">점검 결과</span>
      <div class="results" role="radiogroup" aria-label="점검 결과">
        <button
          v-for="r in ITEM_RESULTS"
          :key="r"
          type="button"
          role="radio"
          :aria-checked="result === r"
          :class="{ on: result === r, warn: r !== ITEM_RESULTS[0] }"
          @click="result = r"
        >
          {{ r }}
        </button>
      </div>

      <label class="label" :for="`memo-${item.id}`">메모 <small>(선택)</small></label>
      <textarea :id="`memo-${item.id}`" v-model="memo" rows="2" maxlength="200" placeholder="예: 벨트 장력 느슨함, 재조정함" />

      <div class="actions">
        <button v-if="last" type="button" class="btn" :disabled="busy" @click="emit('save', { done: false })">체크 풀기</button>
        <button type="button" class="btn btn-primary" :disabled="busy" @click="emit('save', { done: true, result, memo: memo.trim() })">
          <AppIcon name="ok" :size="16" />{{ item.doneAt ? '고쳐 저장' : '완료 체크' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.item {
  margin-bottom: 10px;
  border: 1px solid var(--color-border-card);
  border-radius: 12px;
  background: var(--color-surface);
  transition:
    background 300ms,
    border-color 300ms;
}

.item.done {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.item.open {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.item.locked {
  opacity: 0.6;
}

.line {
  width: 100%;
  min-height: 60px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 12px 12px 14px;
  border: none;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.line:disabled {
  cursor: default;
}

.box {
  width: 28px;
  height: 28px;
  flex: none;
  display: grid;
  place-items: center;
  border: 2px solid var(--color-border-strong);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.done .box {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal);
  color: #fff;
}

.done .box .app-icon {
  stroke-width: 3;
  animation: check-pop 350ms cubic-bezier(0.2, 1.4, 0.3, 1) both;
}

.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.content {
  font-size: 15px;
  font-weight: 600;
  line-height: 21px;
}

.done .content {
  color: var(--color-text-sub);
}

.meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--color-level-normal-text);
}

.meta .warn {
  color: var(--color-level-warn-text);
  font-weight: 600;
}

.memo {
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.chev {
  flex: none;
  color: var(--color-text-weak);
}

.editor {
  padding: 4px 14px 14px;
  animation: softin 200ms both;
}

.label {
  display: block;
  margin: 8px 0 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.label small {
  font-weight: 400;
  color: var(--color-text-weak);
}

.results {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.results button {
  height: 42px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  background: #fff;
  font-size: 13.5px;
  cursor: pointer;
}

.results button.on {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
  font-weight: 700;
}

.results button.warn.on {
  border-color: var(--color-level-warn);
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
}

textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  font-size: 14px;
  resize: vertical;
}

textarea:focus {
  outline: 2px solid var(--color-primary);
  outline-offset: -1px;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.actions .btn {
  flex: 1;
  height: 46px;
  font-size: 15px;
}

@keyframes check-pop {
  from {
    transform: scale(0);
  }
  to {
    transform: scale(1);
  }
}
</style>
