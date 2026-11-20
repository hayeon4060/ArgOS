<!--
  담당자 변경 말풍선 (화면설계서 C3 ⑤)
  - 같은 공정 담당자 중 한 명(지금 담당자 빼고) + 사유(응답 없음 / 부재 / 기타) → '변경'
  - 이미 완료한 항목은 그대로 넘어감 (서버가 처리)
  - 위에서 4px 내려오며 나타남 (motion.css pop-in)
-->
<script setup>
import { ref } from 'vue'
import WorkerPicker from './WorkerPicker.vue'

const props = defineProps({
  workers: { type: Array, required: true },
  processName: { type: String, default: '' },
  currentId: { type: Number, required: true },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['apply', 'cancel'])

const REASONS = ['응답 없음', '부재', '기타']
const assigneeId = ref(props.workers.find((w) => w.id !== props.currentId && w.onDuty)?.id ?? null)
const reason = ref(REASONS[0])
</script>

<template>
  <div class="pop pop-in" role="dialog" aria-label="담당자 변경">
    <b class="title">담당자 변경</b>
    <WorkerPicker v-model="assigneeId" :workers="workers" :except="currentId" :process-name="processName" />
    <div class="reasons" role="radiogroup" aria-label="변경 사유">
      <label v-for="r in REASONS" :key="r" :class="{ on: reason === r }">
        <input v-model="reason" type="radio" :value="r" name="reason" />{{ r }}
      </label>
    </div>
    <div class="m-actions">
      <button type="button" class="btn" @click="emit('cancel')">취소</button>
      <button type="button" class="btn btn-primary" :disabled="!assigneeId || busy" @click="emit('apply', { assigneeId, reason })">
        변경
      </button>
    </div>
  </div>
</template>

<style scoped>
.pop {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  z-index: 5;
  width: 360px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.22);
}

.title {
  display: block;
  margin-bottom: 10px;
  font-size: 14px;
}

.reasons {
  display: flex;
  gap: 6px;
  margin: 10px 0 12px;
}

.reasons label {
  padding: 5px 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  cursor: pointer;
}

.reasons label.on {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 700;
}

.reasons input {
  display: none;
}

.m-actions .btn {
  flex: 1;
  height: 38px;
}
</style>
