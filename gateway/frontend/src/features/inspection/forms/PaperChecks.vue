<!--
  양식의 네모 칸 묶음 (하나만 고름) — 예: □일상 □주간 □월간
  - 보기: 고른 칸에 V
  - 작성(edit): 칸(또는 글자)을 누르면 V, 같은 칸을 다시 누르면 지움. 고른 값은 values[k]
  - label 을 비우면 네모만 (점검 항목 표의 양호 · 불량 · 해당 없음 칸)
-->
<script setup>
const props = defineProps({
  values: { type: Object, required: true },
  k: { type: String, required: true },
  /** [{ value, label }] */
  options: { type: Array, required: true },
  edit: { type: Boolean, default: false },
  /** 네모만 그림 (글자 없이) */
  bare: { type: Boolean, default: false },
})

function pick(value) {
  if (!props.edit) return
  // eslint-disable-next-line vue/no-mutating-props -- 부모가 넘긴 양식 값 객체의 칸 하나를 고침 (PaperInput 과 같음)
  props.values[props.k] = props.values[props.k] === value ? '' : value
}
</script>

<template>
  <span
    v-for="o in options"
    :key="o.value"
    :class="bare ? 'bare' : 'opt'"
    :role="edit ? 'radio' : undefined"
    :aria-checked="edit ? values[k] === o.value : undefined"
    :tabindex="edit ? 0 : undefined"
    @click="pick(o.value)"
    @keydown.space.prevent="pick(o.value)"
    @keydown.enter.prevent="pick(o.value)"
  >
    <i class="cb" :class="{ on: values[k] === o.value }" /><template v-if="!bare">{{ o.label }}</template>
  </span>
</template>
