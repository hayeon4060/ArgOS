<!--
  로그인 입력칸 한 줄: 글자 + 아이콘 달린 입력칸 + 칸 아래 빨간 오류 글씨
  - v-model 로 값 주고받기, 오른쪽 끝에 버튼 등을 넣을 때는 suffix 슬롯 사용
  - 부모에서 focus() 를 부를 수 있게 노출 (오류 난 칸으로 커서 이동용)
-->
<script setup>
import { ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

defineProps({
  id: { type: String, required: true },
  label: { type: String, required: true },
  /** 왼쪽 아이콘 이름 (AppIcon) */
  icon: { type: String, required: true },
  type: { type: String, default: 'text' },
  autocomplete: { type: String, default: 'off' },
  disabled: { type: Boolean, default: false },
  /** 칸 아래에 보일 오류 문구 */
  error: { type: String, default: '' },
  /** 빨간 테두리만 표시 (문구 없이) */
  invalid: { type: Boolean, default: false },
})

const model = defineModel({ type: String, default: '' })

const input = ref(null)
defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <div class="field">
    <label :for="id">{{ label }}</label>
    <div class="input" :class="{ bad: error || invalid }">
      <AppIcon :name="icon" />
      <input
        :id="id"
        ref="input"
        v-model="model"
        :type="type"
        :autocomplete="autocomplete"
        :disabled="disabled"
        spellcheck="false"
      />
      <slot name="suffix" />
    </div>
    <span v-if="error" class="field-error"><AppIcon name="alert" :size="14" />{{ error }}</span>
  </div>
</template>

<style scoped>
.field {
  margin-top: 18px;
}

label {
  display: block;
  margin-bottom: 7px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.input {
  height: 46px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px 0 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 15px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.input:focus-within {
  border-color: var(--color-primary);
}

.input > .app-icon {
  color: var(--color-text-weak);
}

.input.bad {
  border-color: var(--color-level-alarm);
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.12);
}

input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
}

.field-error {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  color: var(--color-level-alarm-text);
  font-size: 12.5px;
  font-weight: 600;
}
</style>
