<!--
  체크리스트 목록 (API 명세서 M2 GET /me/checklists, 아래 '체크리스트' 탭)
  - 진행 중(새로 받음 · 진행 중)이 위, 끝난 것(보고함 · 확인 완료)이 아래
  - 데이터는 레이아웃이 불러 둔 stores/myChecklists 를 그대로 씀
-->
<script setup>
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import { CHECKLIST_STATUS_LABEL } from '../codes'
import { useMyChecklistsStore } from '../stores/myChecklists'

const my = useMyChecklistsStore()

const SECTIONS = [
  { key: 'active', title: '진행 중', empty: '받은 체크리스트가 없습니다.' },
  { key: 'done', title: '끝난 체크리스트', empty: '아직 끝낸 체크리스트가 없습니다.' },
]
</script>

<template>
  <div>
    <p v-if="!my.loaded" class="state">불러오는 중</p>

    <section v-for="s in SECTIONS" v-else :key="s.key">
      <h2>{{ s.title }} <small>{{ my[s.key].length }}</small></h2>
      <RouterLink
        v-for="c in my[s.key]"
        :key="c.id"
        :to="{ name: 'checklist', params: { checklistId: c.id } }"
        class="row"
        :class="{ fresh: c.status === 'SENT' }"
      >
        <ProgressRing
          :value="c.doneCount"
          :total="c.totalCount"
          :size="46"
          :stroke="5"
          :color="s.key === 'done' ? 'var(--color-level-normal)' : 'var(--color-primary)'"
        >
          <span class="mono count">{{ c.doneCount }}/{{ c.totalCount }}</span>
        </ProgressRing>
        <span class="text">
          <span class="head">
            <LevelBadge :level="c.level" />
            <span class="status">{{ CHECKLIST_STATUS_LABEL[c.status] ?? c.status }}</span>
          </span>
          <b>{{ c.title }}</b>
          <span class="at">받은 시각 <span class="mono">{{ c.sentAt.slice(11, 16) }}</span></span>
        </span>
        <AppIcon name="cr" :size="20" class="chev" />
      </RouterLink>
      <p v-if="!my[s.key].length" class="state small">{{ s.empty }}</p>
    </section>
  </div>
</template>

<style scoped>
h2 {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 4px 0 10px;
  font-size: 15px;
}

section + section h2 {
  margin-top: 24px;
}

h2 small {
  font-size: 13px;
  color: var(--color-text-weak);
}

.row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
  padding: 14px;
  border: 1px solid var(--color-border-card);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--color-text);
  text-decoration: none;
}

.row.router-link-active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.row.fresh {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.count {
  font-size: 11px;
  font-weight: 600;
}

.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.text b {
  font-size: 15px;
  word-break: keep-all; /* 좁은 목록(폴드 펼침)에서 낱말 가운데서 끊지 않게 */
}

.at {
  font-size: 12px;
  color: var(--color-text-weak);
}

.chev {
  color: var(--color-text-weak);
}

.state {
  margin-top: 20px;
  text-align: center;
  color: var(--color-text-sub);
}

.state.small {
  margin-top: 0;
  font-size: 13px;
}
</style>
