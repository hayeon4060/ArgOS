<!--
  조치 체크리스트 항목 고르기 (설계안 mWarnDetail() 오른쪽, 화면설계서 C2 ⑥)
  - 기본 항목(GET /checklist-templates)을 보여 주고, X 로 빼거나 아래 칸에 써서 추가
  - 최소 1개는 남아야 함 (마지막 하나는 X 가 안 눌림)
  - 처음 나타날 때 한 줄씩 차례로 (설계안 .ck fadein 120ms + 70ms 간격), 빠질 때 다른 줄이 150ms 에 자리를 메움
  - 끌어서 순서 바꾸기(화면설계서)는 아직 안 만듦
  - readonly: 이미 보낸 체크리스트를 다시 볼 때
-->
<script setup>
import { ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  readonly: { type: Boolean, default: false },
})

const items = defineModel({ type: Array, required: true })

const draft = ref('')

function add() {
  const text = draft.value.trim()
  if (!text) return
  items.value = [...items.value, text]
  draft.value = ''
}

function remove(index) {
  if (items.value.length <= 1) return
  items.value = items.value.filter((_, i) => i !== index)
}

const pad = (n) => String(n).padStart(2, '0')
</script>

<template>
  <div>
    <TransitionGroup tag="div" name="ck" class="list">
      <div v-for="(text, i) in items" :key="text" class="ck" :style="{ animationDelay: `${120 + i * 70}ms` }">
        <span class="no mono">{{ pad(i + 1) }}</span>
        <span class="text">{{ text }}</span>
        <button
          v-if="!readonly"
          type="button"
          class="x"
          :disabled="items.length <= 1"
          :aria-label="`${text} 빼기`"
          :title="items.length <= 1 ? '최소 1개는 있어야 보낼 수 있습니다' : '빼기'"
          @click="remove(i)"
        >
          <AppIcon name="x" :size="14" />
        </button>
      </div>
    </TransitionGroup>

    <form v-if="!readonly" class="add" @submit.prevent="add">
      <input v-model="draft" type="text" placeholder="항목 직접 추가" maxlength="60" />
      <button type="submit" class="btn" :disabled="!draft.trim()"><AppIcon name="plus" :size="14" />추가</button>
    </form>
  </div>
</template>

<style scoped>
.list {
  position: relative;
}

/* 항목 줄 (설계안 .ck) */
.ck {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 46px;
  margin-bottom: 8px;
  padding: 0 8px 0 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 14px;
  white-space: nowrap;
  animation: softin 300ms ease-out both; /* base.css: 차례로 나타남 */
}

.no {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-weak);
}

.text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.x {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: var(--radius-xs);
  background: none;
  color: var(--color-text-weak);
  cursor: pointer;
}

.x:hover:not(:disabled) {
  background: #f1f5f9;
  color: var(--color-level-alarm-text);
}

.x:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.add {
  display: flex;
  gap: 8px;
}

.add input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 13.5px;
}

.add .btn {
  height: 38px;
  padding: 0 12px;
  font-size: 13px;
}

/* 빠질 때: 흐려지며 사라지고, 아래 줄이 150ms 에 올라와 자리를 메움 */
.ck-move {
  transition: transform 150ms ease-out;
}

.ck-leave-active {
  position: absolute;
  left: 0;
  right: 0;
  transition: opacity 150ms ease-in;
}

.ck-leave-to {
  opacity: 0;
}
</style>
