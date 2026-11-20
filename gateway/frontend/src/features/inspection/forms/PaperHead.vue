<!--
  양식 머리: 제목 · 양식 번호 상자 · 결재 칸 (원본 .head 그대로)
  - 결재 '점검' 칸에는 점검자 이름 (보기일 때만)
-->
<script setup>
defineProps({
  def: { type: Object, required: true },
  inspector: { type: String, default: '' },
})
</script>

<template>
  <i class="mk tl" /><i class="mk tr" /><i class="mk bl" /><i class="mk br" />
  <div class="head">
    <div class="ttl">
      <div class="sys">ArgOS 환경안전관제 · 서류 디지털화 대상 문서</div>
      <h1>{{ def.title }}</h1>
      <div class="sub">{{ def.sub }}</div>
    </div>
    <div class="code">
      <b>{{ def.code }}</b><span>Rev.0</span><span>No. <span class="blank">______</span></span>
    </div>
    <table class="appr">
      <tbody>
        <tr class="r1">
          <th class="v" rowspan="2">결재</th>
          <td>점검</td>
          <td>검토</td>
          <td>승인</td>
        </tr>
        <tr class="r2">
          <td><span v-if="inspector" class="hw">{{ inspector }}</span></td>
          <td />
          <td />
        </tr>
      </tbody>
    </table>
  </div>
</template>
