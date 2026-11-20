<!--
  서류 양식 한 장 (F02 · F03) — 보기 · 작성 · 스캔본 모양 공통 틀
  - 사용 예: <PaperForm form-type="F02" :values="values" edit />
  - values: reactive 객체. 작성(edit)이면 칸을 눌러 적은 값이 이 객체에 바로 들어감
  - scan: 예시 모드에서 스캔 원본처럼 보이게 (종이 색 · 살짝 기울임 · 손글씨)
-->
<script setup>
import F02Form from './F02Form.vue'
import F03Form from './F03Form.vue'
import './paperForm.css'

defineProps({
  formType: { type: String, required: true },
  values: { type: Object, required: true },
  edit: { type: Boolean, default: false },
  scan: { type: Boolean, default: false },
  equipmentName: { type: String, default: '' },
})
</script>

<template>
  <div class="af" :class="{ edit, scan }">
    <div class="af-page">
      <F02Form v-if="formType === 'F02'" :values="values" :edit="edit" :equipment-name="equipmentName" />
      <F03Form v-else :values="values" :edit="edit" />
    </div>
  </div>
</template>
