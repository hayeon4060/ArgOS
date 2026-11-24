<!--
  F02 환기·배기설비 점검일지 (팀 양식 원본 F02 쪽 그대로)
  1 점검 정보 · 2 점검 항목(12) · 3 이상 사항 및 조치(3줄) · 4 종합 판정
-->
<script setup>
import PaperChecks from './PaperChecks.vue'
import PaperHead from './PaperHead.vue'
import PaperInput from './PaperInput.vue'
import PaperIssues from './PaperIssues.vue'
import PaperItems from './PaperItems.vue'
import { FACTORY_OPTIONS, FORM_DEFS } from './formDefs'

defineProps({
  values: { type: Object, required: true },
  edit: { type: Boolean, default: false },
  /** 설비 이름 (설비번호 옆 괄호) */
  equipmentName: { type: String, default: '' },
})

const def = FORM_DEFS.F02
</script>

<template>
  <PaperHead :def="def" :inspector="edit ? '' : values.inspector" />

  <h2><span class="n">1</span>점검 정보</h2>
  <table class="f h7">
    <colgroup><col style="width: 22mm" /><col /><col style="width: 22mm" /><col style="width: 58mm" /></colgroup>
    <tbody>
      <tr>
        <th>점검 일자</th>
        <td><PaperInput :values="values" k="inspectedAt" type="datetime" blank="20____ . ____ . ____   ____ : ____" :edit="edit" /></td>
        <th>점검 구분</th>
        <td><PaperChecks :values="values" k="inspType" :options="def.inspTypes" :edit="edit" /></td>
      </tr>
      <tr>
        <th>공장</th>
        <td><PaperChecks :values="values" k="factory" :options="FACTORY_OPTIONS" :edit="edit" /></td>
        <th>설비번호</th>
        <td>
          <span class="cell">
            <PaperInput :values="values" k="equipmentNo" prefix="EF -" blank="________" :edit="edit" />
            <span v-if="equipmentName" class="hw">({{ equipmentName }})</span>
          </span>
        </td>
      </tr>
      <tr>
        <th>구역</th>
        <td colspan="3"><PaperChecks :values="values" k="zone" :options="def.zones" :edit="edit" /></td>
      </tr>
      <tr>
        <th>점검자</th>
        <td><PaperInput :values="values" k="inspector" :edit="edit" /></td>
        <th>확인자</th>
        <td><PaperInput :values="values" k="checker" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>

  <h2><span class="n">2</span>점검 항목<small>결과에 V 표시, 측정값은 숫자로 기입</small></h2>
  <PaperItems :items="def.items" :values="values" :edit="edit" />
  <div class="note">※ 회색 칸은 측정값이 없는 항목입니다. CO 감지기 측정값은 점검 시점의 지시값을 적습니다.</div>

  <h2><span class="n">3</span>이상 사항 및 조치</h2>
  <PaperIssues :rows="def.issueRows" :values="values" :edit="edit" />

  <h2><span class="n">4</span>종합 판정</h2>
  <table class="f h8">
    <colgroup><col style="width: 22mm" /><col /></colgroup>
    <tbody>
      <tr>
        <th>판정</th>
        <td><PaperChecks :values="values" k="verdict" :options="def.verdicts" :edit="edit" /></td>
      </tr>
      <tr>
        <th>종합 의견</th>
        <td style="height: 14mm"><PaperInput :values="values" k="opinion" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>

  <div class="foot"><span>ArgOS 시연용 가상 양식 · 실제 사업장 공식 양식이 아님</span><span>ARG-F02 · 1 / 1</span></div>
</template>
