<!--
  이름 하나 입력하는 창 — 사업장 · 공장 · 공정 추가에 같이 씀 (화면설계안 v-pages2.js addScope() 창)
  - 제목 + 상위 위치(예: '창원공장 아래') + 이름 칸 + 취소 / 추가
  - suffix: 이름 끝에 붙일 말 (사업장 '공장': '창원' → '창원공장', 이미 '공장'으로 끝나면 그대로)
  - taken: 이미 있는 이름들 → 같은 이름이면 추가 단추를 끄고 안내
  - 입력값 검사만 하고, 실제 추가(API)는 부모가 submit(최종 이름)을 받아서 함
  - 사용 예: <NameInputModal :open="show" title="공장 추가" sub="창원공장 아래" label="공장 이름" @submit="add" @cancel="show = false" />
-->
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import BaseModal from './BaseModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  /** 제목 옆 회색 글씨 (어디 아래에 추가되는지) */
  sub: { type: String, default: '' },
  label: { type: String, default: '이름' },
  placeholder: { type: String, default: '' },
  /** 이름 끝에 붙일 말 (없으면 입력 그대로) */
  suffix: { type: String, default: '' },
  /** 이미 있는 이름들 */
  taken: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['submit', 'cancel'])

const MAX = 50 // DB name varchar(50)
const text = ref('')
const input = ref(null)

// 창이 열릴 때마다 칸을 비우고 바로 입력할 수 있게
watch(
  () => props.open,
  async (open) => {
    if (!open) return
    text.value = ''
    await nextTick()
    input.value?.focus()
  },
)

/** 실제로 만들어질 이름 */
const finalName = computed(() => {
  const t = text.value.trim()
  if (!t || !props.suffix || t.endsWith(props.suffix)) return t
  return t + props.suffix
})

/** 막는 이유 (없으면 빈 글자) */
const error = computed(() => {
  if (finalName.value.length > MAX) return `${MAX}자까지 입력할 수 있습니다.`
  if (props.taken.includes(finalName.value)) return `같은 이름(${finalName.value})이 이미 있습니다.`
  return ''
})
const canSubmit = computed(() => !!finalName.value && !error.value && !props.busy)

function submit() {
  if (canSubmit.value) emit('submit', finalName.value)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal" :duration="{ enter: 300, leave: 200 }">
      <BaseModal v-if="open" :width="440" :label="title" @close="emit('cancel')">
        <form class="name-modal" novalidate autocomplete="off" @submit.prevent="submit">
          <div class="head">
            <h2>{{ title }}</h2>
            <span v-if="sub" class="sub">{{ sub }}</span>
          </div>

          <label class="field">
            <span class="lb">{{ label }}</span>
            <input ref="input" v-model="text" type="text" :placeholder="placeholder" :maxlength="MAX + 10" :aria-invalid="!!error" />
          </label>
          <p v-if="error" class="msg err"><AppIcon name="alert" :size="14" />{{ error }}</p>
          <p v-else-if="suffix && finalName" class="msg">‘{{ finalName }}’(으)로 만들어집니다.</p>

          <div class="m-actions">
            <button type="button" class="btn" @click="emit('cancel')">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="!canSubmit">
              <span v-if="busy" class="m-spin" /><AppIcon v-else name="plus" :size="16" />{{ busy ? '추가 중' : '추가' }}
            </button>
          </div>
        </form>
      </BaseModal>
    </Transition>
  </Teleport>
</template>

<style scoped>
.name-modal {
  padding: 24px 24px 20px;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

h2 {
  font-size: 18px;
  font-weight: 700;
}

.sub {
  font-size: 14px;
  color: var(--color-text-sub);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 18px;
}

.lb {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

input {
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--color-text);
  outline: none;
  transition: border-color var(--m-hover), box-shadow var(--m-hover);
}

input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

input[aria-invalid='true'] {
  border-color: var(--color-level-alarm);
}

.msg {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--color-text-sub);
}

.msg.err {
  color: var(--color-level-alarm-text);
}

.m-actions {
  justify-content: flex-end;
  margin-top: 22px;
}

.m-actions .btn {
  height: 40px;
  padding: 0 18px;
}
</style>
