<!--
  점검 이력 표
  - 열: 점검일시 · 서류 양식 · 설비 · 점검자 · 점검 상태 · 구분 · 결과 · 스캔 파일 (열 제목 · 값 모두 가운데 — 수정안 5)
  - 예정 · 누락 건은 구분 · 결과가 비어 있음(–)
  - 누락 줄은 빨간색 (줄 바탕 연한 빨강 + 상태 배지 빨강 — 수정안 3)
  - 수기 줄 맨 오른쪽: 미리보기(돋보기 — 수정안 6) · 내려받기. 전자 줄은 버튼 없음
  - 줄을 누르면 open(점검 건), 미리보기 scan(점검 건), 내려받기 download(점검 건)
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'
import InspBadge from './InspBadge.vue'
import { FORM_LABEL, RESULT, SCHEDULE_STATE, SOURCE } from './inspectionMeta'

defineProps({
  rows: { type: Array, required: true },
  today: { type: String, required: true },
})

defineEmits(['open', 'scan', 'download'])
</script>

<template>
  <table class="tb">
    <colgroup>
      <col style="width: 146px" />
      <col />
      <col style="width: 21%" />
      <col style="width: 72px" />
      <col style="width: 80px" />
      <col style="width: 62px" />
      <col style="width: 62px" />
      <col style="width: 84px" />
    </colgroup>
    <thead>
      <tr>
        <th>점검일시</th>
        <th>서류 양식</th>
        <th>설비</th>
        <th>점검자</th>
        <th>점검 상태</th>
        <th>구분</th>
        <th>결과</th>
        <th>스캔 파일</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="it in rows"
        :key="it.scheduleId"
        class="r"
        :class="{ miss: it.status === 'DELAYED', now: it.date === today }"
        tabindex="0"
        @click="$emit('open', it)"
        @keydown.enter="$emit('open', it)"
      >
        <td class="dt mono">{{ it.date }}<small>{{ it.time }}</small></td>
        <td class="ell" :title="`${it.formType} ${FORM_LABEL[it.formType]}`"><span class="fc mono">{{ it.formType }}</span> {{ FORM_LABEL[it.formType] }}</td>
        <td class="ell" :title="it.equipmentName"><b>{{ it.equipmentName }}</b></td>
        <td>{{ it.assignee }}</td>
        <td><InspBadge :tone="SCHEDULE_STATE[it.status].cls" :solid="it.status === 'DELAYED'">{{ SCHEDULE_STATE[it.status].label }}</InspBadge></td>
        <td>
          <InspBadge v-if="it.source" :tone="SOURCE[it.source].cls">{{ SOURCE[it.source].label }}</InspBadge>
          <span v-else class="none">–</span>
        </td>
        <td>
          <InspBadge v-if="it.result" :tone="RESULT[it.result].cls">{{ RESULT[it.result].label }}</InspBadge>
          <span v-else class="none">–</span>
        </td>
        <td>
          <span v-if="it.source === 'PAPER'" class="acts">
            <button type="button" class="ibtn" title="스캔 원본 미리보기" aria-label="스캔 원본 미리보기" @click.stop="$emit('scan', it)">
              <AppIcon name="search" :size="15" />
            </button>
            <button type="button" class="ibtn" title="스캔 파일 내려받기" aria-label="스캔 파일 내려받기" @click.stop="$emit('download', it)">
              <AppIcon name="download" :size="15" />
            </button>
          </span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.tb {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13.5px;
}

/* 열 제목 · 값 모두 가운데 (수정안 5) */
th,
td {
  text-align: center;
}

th {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 38px;
  padding: 0 8px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-sub);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-sub);
  white-space: nowrap;
}

td {
  height: 44px;
  padding: 0 8px;
  border-bottom: 1px solid #f1f5f9;
  white-space: nowrap;
}

.ell {
  overflow: hidden;
  text-overflow: ellipsis;
}

.r {
  cursor: pointer;
  transition: background var(--m-hover);
}

.r:hover {
  background: var(--color-surface-sub);
}

/* 누락 줄: 연한 빨강 바탕 (수정안 3) */
.r.miss {
  background: #fef2f2;
}

.r.miss:hover {
  background: var(--color-level-alarm-soft);
}

.r.miss .dt,
.r.miss b {
  color: var(--color-level-alarm-text);
}

.dt {
  font-size: 12.5px;
}

.dt small {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 오늘 점검은 날짜를 진하게 (맨 위 묶음) */
.now .dt {
  font-weight: 600;
}

.fc {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-primary);
}

b {
  font-weight: 600;
}

.none {
  color: var(--color-text-weak);
}

.acts {
  display: inline-flex;
  gap: 4px;
  vertical-align: middle;
}

.ibtn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--color-text-sub);
  cursor: pointer;
  transition:
    color var(--m-hover),
    border-color var(--m-hover);
}

.ibtn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>
