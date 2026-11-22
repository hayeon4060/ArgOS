<!--
  권한별 사용 범위 (화면설계안 vUsers() #mx — 읽기 전용 표)
  - 권한 3종 × 기능, 할 수 있으면 체크, 못 하면 '–'
  - 2026-11-22 (재원님 요청): 화면 높이가 낮으면 표 아래가 잘리던 것
    → 왼쪽 칸에 남는 높이만큼만 카드를 두고 표만 세로 스크롤 (제목줄 · 표 머리글은 고정)
-->
<script setup>
import { MATRIX } from './userMeta'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
</script>

<template>
  <BaseCard title="권한별 사용 범위">
    <div class="scroll">
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
    </div>
  </BaseCard>
</template>

<style scoped>
/*
 * 카드 = 제목줄(고정) + 표(넘치면 세로 스크롤)
 * - 높이가 넉넉하면 예전처럼 표 길이만큼, 모자라면 남는 높이까지 줄고 표만 스크롤
 */
.base-card {
  display: flex;
  flex-direction: column;
  flex: 0 1 auto;
  min-height: 0;
}

.scroll {
  flex: 0 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

/* 스크롤해도 표 머리글은 위에 남음 */
thead th {
  position: sticky;
  top: 0;
  z-index: 1;
}

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
