<!--
  서류 양식 한 장 (F02 환기·배기설비 점검일지) — 보기 · 작성 · 스캔본 모양 공통 틀
  - 사용 예: <PaperForm :values="values" edit />
  - values: reactive 객체. 작성(edit)이면 칸을 눌러 적은 값이 이 객체에 바로 들어감
  - scan: 예시 모드에서 스캔 원본처럼 보이게 (종이 색 · 살짝 기울임 · 손글씨)
  - 점검 관리는 F02 한 가지만 씀 (변전소 F03 은 2026-11-20 에 뺌). 다른 양식이 생기면 여기서 갈라 그림
-->
<script setup>
import F02Form from './F02Form.vue'
import './paperForm.css'

defineProps({
  values: { type: Object, required: true },
  edit: { type: Boolean, default: false },
  scan: { type: Boolean, default: false },
  equipmentName: { type: String, default: '' },
})
</script>

<template>
  <div class="af" :class="{ edit, scan }">
    <div class="af-page">
      <F02Form :values="values" :edit="edit" :equipment-name="equipmentName" />
    </div>
  </div>
</template>
