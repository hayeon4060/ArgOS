<!--
  양식의 '이상 사항 및 조치' 표: 항목 No · 이상 내용 · 조치 내용 · 조치 기한 · 조치 완료일 (n줄)
-->
<script setup>
import PaperInput from './PaperInput.vue'

defineProps({
  rows: { type: Number, required: true },
  values: { type: Object, required: true },
  edit: { type: Boolean, default: false },
})
</script>

<template>
  <table class="f h8">
    <colgroup>
      <col style="width: 16mm" /><col /><col /><col style="width: 26mm" /><col style="width: 26mm" />
    </colgroup>
    <tbody>
      <tr>
        <th>항목 No</th>
        <th>이상 내용</th>
        <th>조치 내용</th>
        <th>조치 기한</th>
        <th>조치 완료일</th>
      </tr>
      <tr v-for="r in rows" :key="r">
        <td class="c"><PaperInput :values="values" :k="`issue${r}No`" :edit="edit" /></td>
        <td><PaperInput :values="values" :k="`issue${r}Problem`" :edit="edit" /></td>
        <td><PaperInput :values="values" :k="`issue${r}Action`" :edit="edit" /></td>
        <td class="c"><PaperInput :values="values" :k="`issue${r}Due`" type="date" blank="__ . __ . __" :edit="edit" /></td>
        <td class="c"><PaperInput :values="values" :k="`issue${r}DoneAt`" type="date" blank="__ . __ . __" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>
</template>
