<!--
  양식의 적는 칸 하나 (값 + 단위)
  - 보기: 적힌 값을 파란 손글씨 색으로, 비었으면 양식의 밑줄 글자(blank)
  - 작성(edit): 노란 입력칸. 고친 값은 values[k] 에 바로 들어감
    (values 는 부모 PaperForm 이 넘긴 reactive 객체 — 칸마다 v-model 을 따로 두지 않으려고 안쪽 키만 바꿈)
  - type: text / date (조치 기한 '__ . __ . __') / datetime (점검 일자 · 시각)
-->
<script setup>
import { computed } from 'vue'
import { longDateTime, shortDate } from './formDefs'

const props = defineProps({
  values: { type: Object, required: true },
  k: { type: String, required: true },
  edit: { type: Boolean, default: false },
  type: { type: String, default: 'text' },
  unit: { type: String, default: '' },
  /** 비었을 때 보이는 양식 밑줄 글자 */
  blank: { type: String, default: '' },
  /** 값 앞에 인쇄된 글자 (설비번호 'EF - ') */
  prefix: { type: String, default: '' },
})

/** 보기용 글자 */
const shown = computed(() => {
  const v = props.values[props.k]
  if (!v) return ''
  if (props.type === 'date') return shortDate(v)
  if (props.type === 'datetime') return longDateTime(v)
  return v
})

const inputType = computed(() => ({ date: 'date', datetime: 'datetime-local' })[props.type] ?? 'text')
</script>

<template>
  <span class="cell">
    <span v-if="prefix" class="pre">{{ prefix }}</span>
    <input v-if="edit" v-model="values[k]" class="ed" :type="inputType" :aria-label="k" />
    <span v-else-if="shown" class="hw">{{ shown }}</span>
    <span v-else class="blank">{{ blank }}</span>
    <span v-if="unit" class="unit">{{ unit }}</span>
  </span>
</template>
