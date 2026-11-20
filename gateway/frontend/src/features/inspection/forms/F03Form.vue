<!--
  F03 수변전설비 점검일지 (팀 양식 원본 F03 쪽 그대로)
  1 점검 정보 · 2 계측 기록 · 3 점검 항목(10) · 4 이상 사항 및 조치(2줄) + 종합 판정
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
})

const def = FORM_DEFS.F03
/** 계측 기록 3상 (R · S · T) */
const PHASES = ['R', 'S', 'T']
const top0 = { borderTop: 0 }
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
        <th>전기실명</th>
        <td><PaperInput :values="values" k="roomName" :edit="edit" /></td>
      </tr>
      <tr>
        <th>설비번호</th>
        <td><PaperInput :values="values" k="equipmentNo" prefix="TR -" blank="________" :edit="edit" /></td>
        <th>수전 전압</th>
        <td><PaperChecks :values="values" k="voltage" :options="def.voltages" :edit="edit" /></td>
      </tr>
      <tr>
        <th>점검자</th>
        <td><PaperInput :values="values" k="inspector" :edit="edit" /></td>
        <th>확인자</th>
        <td><PaperInput :values="values" k="checker" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>

  <h2><span class="n">2</span>계측 기록<small>계기 지시값을 숫자로 기입</small></h2>
  <table class="f h8">
    <colgroup><col style="width: 32mm" /><col /><col /><col /><col style="width: 30mm" /></colgroup>
    <tbody>
      <tr>
        <th>구분</th>
        <th>R상 (L1)</th>
        <th>S상 (L2)</th>
        <th>T상 (L3)</th>
        <th>비고</th>
      </tr>
      <tr>
        <th>전압</th>
        <td v-for="p in PHASES" :key="p"><PaperInput :values="values" :k="`volt${p}`" unit="V" :edit="edit" /></td>
        <td />
      </tr>
      <tr>
        <th>전류</th>
        <td v-for="p in PHASES" :key="p"><PaperInput :values="values" :k="`amp${p}`" unit="A" :edit="edit" /></td>
        <td />
      </tr>
    </tbody>
  </table>
  <table class="f h8" :style="top0">
    <colgroup><col style="width: 32mm" /><col /><col style="width: 32mm" /><col /></colgroup>
    <tbody>
      <tr>
        <th :style="top0">최대 수요전력</th>
        <td :style="top0"><PaperInput :values="values" k="peakKw" unit="kW" :edit="edit" /></td>
        <th :style="top0">부하율</th>
        <td :style="top0"><PaperInput :values="values" k="loadRate" unit="%" :edit="edit" /></td>
      </tr>
      <tr>
        <th>역률</th>
        <td><PaperInput :values="values" k="powerFactor" unit="%" :edit="edit" /></td>
        <th>변압기 온도</th>
        <td><PaperInput :values="values" k="trTemp" unit="℃" :edit="edit" /></td>
      </tr>
      <tr>
        <th>적산 전력량</th>
        <td colspan="3"><PaperInput :values="values" k="kwh" unit="kWh" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>

  <h2><span class="n">3</span>점검 항목<small>결과에 V 표시</small></h2>
  <PaperItems :items="def.items" :values="values" :edit="edit" row-class="h7" />

  <h2><span class="n">4</span>이상 사항 및 조치</h2>
  <PaperIssues :rows="def.issueRows" :values="values" :edit="edit" />
  <table class="f h8" style="margin-top: 2mm">
    <colgroup><col style="width: 22mm" /><col /></colgroup>
    <tbody>
      <tr>
        <th>종합 판정</th>
        <td><PaperChecks :values="values" k="verdict" :options="def.verdicts" :edit="edit" /></td>
      </tr>
    </tbody>
  </table>

  <div class="foot"><span>ArgOS 시연용 가상 양식 · 실제 사업장 공식 양식이 아님</span><span>ARG-F03 · 1 / 1</span></div>
</template>
