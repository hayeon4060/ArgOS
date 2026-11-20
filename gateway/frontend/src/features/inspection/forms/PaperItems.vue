<!--
  양식의 '점검 항목' 표: No · 점검 항목 · 점검 기준 · 양호 · 불량 · 해당 없음 · 측정값 · 비고
  - 결과 3칸은 한 묶음(item01 …)이라 하나만 V
  - 측정값 단위가 없는 항목은 회색 칸 (원본 그대로)
-->
<script setup>
import PaperChecks from './PaperChecks.vue'
import PaperInput from './PaperInput.vue'
import { RESULT_OPTIONS, itemKey } from './formDefs'

defineProps({
  /** [[항목, 기준, 단위]] */
  items: { type: Array, required: true },
  values: { type: Object, required: true },
  edit: { type: Boolean, default: false },
  /** 줄 높이 class (h7 · h8) */
  rowClass: { type: String, default: 'h8' },
})

const one = (v) => [{ value: v, label: v }]
</script>

<template>
  <table class="f" :class="rowClass">
    <colgroup>
      <col style="width: 8mm" /><col style="width: 30mm" /><col /><col style="width: 13mm" /><col style="width: 13mm" /><col style="width: 13mm" /><col style="width: 27mm" /><col style="width: 22mm" />
    </colgroup>
    <tbody>
      <tr>
        <th>No</th>
        <th>점검 항목</th>
        <th>점검 기준</th>
        <th>양호</th>
        <th>불량</th>
        <th>해당<br />없음</th>
        <th>측정값</th>
        <th>비고</th>
      </tr>
      <tr v-for="([label, rule, unit], i) in items" :key="i">
        <td class="c">{{ i + 1 }}</td>
        <td>{{ label }}</td>
        <td>{{ rule }}</td>
        <td v-for="r in RESULT_OPTIONS" :key="r" class="c">
          <PaperChecks :values="values" :k="itemKey(i + 1)" :options="one(r)" :edit="edit" bare />
        </td>
        <td v-if="unit"><PaperInput :values="values" :k="`${itemKey(i + 1)}Value`" :unit="unit" :edit="edit" /></td>
        <td v-else class="gray" />
        <td><PaperInput :values="values" :k="`${itemKey(i + 1)}Note`" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>
</template>
