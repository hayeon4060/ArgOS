<!--
  권한별 사용 범위 (화면설계안 vUsers() #mx — 읽기 전용 표)
  - 권한 3종 × 기능, 할 수 있으면 체크, 못 하면 '–'
-->
<script setup>
import { MATRIX } from './userMeta'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
</script>

<template>
  <BaseCard title="권한별 사용 범위">
    <table class="mx">
      <thead>
        <tr><th /><th>본사 관리자</th><th>공장 담당자</th><th>공정 담당자</th></tr>
      </thead>
      <tbody>
        <tr v-for="row in MATRIX" :key="row[0]">
          <td>{{ row[0] }}</td>
          <td v-for="(c, i) in row.slice(1)" :key="i" :class="{ no: !c }">
            <AppIcon v-if="c === 'ok'" name="check" :size="15" class="ok" />
            <template v-else>{{ c || '–' }}</template>
          </td>
        </tr>
      </tbody>
    </table>
  </BaseCard>
</template>

<style scoped>
.mx {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

th {
  height: 40px;
  padding: 0 6px;
  background: var(--color-surface-sub);
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  color: var(--color-text-sub);
  text-align: center;
}

td {
  height: 48px;
  padding: 0 6px;
  border-bottom: 1px solid #f1f5f9;
  text-align: center;
  color: var(--color-text);
}

td:first-child {
  padding-left: 16px;
  font-weight: 500;
  text-align: left;
  color: var(--color-text-sub);
}

td.no {
  color: #cbd5e1;
}

.ok {
  color: var(--color-level-normal);
  stroke-width: 3;
}
</style>
