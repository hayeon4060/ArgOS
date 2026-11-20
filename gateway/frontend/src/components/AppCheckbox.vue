<!--
  체크박스 (설계안 .cb 모양: 파란 네모 + 흰 체크)
  - 실제 체크박스는 숨기고 모양만 바꿈 → 키보드(스페이스)·스크린리더 그대로 동작
  - 사용 예: <AppCheckbox v-model="rememberId">아이디 저장</AppCheckbox>
-->
<script setup>
import AppIcon from './AppIcon.vue'

const checked = defineModel({ type: Boolean, default: false })
</script>

<template>
  <label class="app-checkbox">
    <input v-model="checked" type="checkbox" />
    <span class="box"><AppIcon name="ok" :size="11" /></span>
    <slot />
  </label>
</template>

<style scoped>
.app-checkbox {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

input {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
}

.box {
  width: 16px;
  height: 16px;
  margin-right: 7px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--color-border-strong);
  border-radius: 3px;
  color: transparent;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.box :deep(.app-icon) {
  stroke-width: 3.5;
}

input:checked + .box {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

input:focus-visible + .box {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
</style>
