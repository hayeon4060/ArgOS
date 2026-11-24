<!--
  점검 관리 (앱) — 관제 웹 점검 관리 화면(@/pages/inspection/InspectionPage.vue)을 그대로 씀
  - 웹은 1920 기준 가로 배치(달력 | 점검 이력)라 휴대폰 폭에서는 위아래 한 줄로만 바꿔 줌
  - 수기 서류 등록 · 스케줄 추가 · 전자 작성 권한은 웹과 같음 (본사 관리자만 열람 전용)
-->
<script setup>
import WebInspectionPage from '@/pages/inspection/InspectionPage.vue'
</script>

<template>
  <div class="app-insp">
    <WebInspectionPage />
  </div>
</template>

<style scoped>
/* 안의 표 · 도구 줄이 넓어도 화면 폭을 넘지 않게 (넘는 표는 아래에서 좌우로 밀어 봄) */
.app-insp {
  contain: inline-size;
}

/* 웹은 한 줄 높이 48 고정 — 좁은 폭에서는 제목 줄 · 서류 양식 바가 두 줄로 내려가게 */
.app-insp :deep(.tools),
.app-insp :deep(.card-head),
.app-insp :deep(.tplbar) {
  flex-wrap: wrap;
  height: auto;
  min-height: 48px;
  padding-top: 8px;
  padding-bottom: 8px;
  row-gap: 8px;
}

.app-insp :deep(.card-head .sub) {
  overflow: visible;
}

.app-insp :deep(.tplbar h3),
.app-insp :deep(.tplbar .tpl) {
  white-space: nowrap;
}

.app-insp :deep(.hist .scroll) {
  overflow-x: auto;
}

.app-insp :deep(.hist .tb) {
  min-width: 720px;
}

.app-insp :deep(.inspection-page) {
  height: auto;
}

.app-insp :deep(.lay) {
  display: flex;
  flex-direction: column;
}

/* 달력 · 이력 카드가 화면 높이를 채우려는 웹 배치 대신 내용 높이로 */
.app-insp :deep(.g-cal),
.app-insp :deep(.g-hist) {
  min-height: 520px;
}
</style>

<style>
/* 웹 창(BaseModal)은 866px 같은 고정 폭 — 휴대폰에서는 화면 폭에 맞추고 안에서 좌우로 밀어 봄 */
.modal-root {
  grid-template-columns: minmax(0, 1fr);
  padding: 12px;
}
</style>
