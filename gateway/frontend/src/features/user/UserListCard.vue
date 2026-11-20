<!--
  사용자 목록 (화면설계안 vUsers() list())
  - 위: 권한 탭(전체 / 본사 관리자 / 공장 담당자 / 공정 담당자), 공장 선택 (공장 담당자는 소속 공장 고정)
  - 열: 이름(나는 '나'), 아이디, 권한, 담당 범위, 최근 접속(나는 '접속 중'), 상태 — 한 쪽 15행
  - 줄을 누르면 pick(id) → 오른쪽 패널이 그 사용자로. 고른 줄은 파란 막대, 방금 저장한 줄은 2초 강조
-->
<script setup>
import { computed } from 'vue'
import { ROLE, ROLE_COLOR, ROLE_LABEL, shortTime } from './userMeta'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import SegButtons from '@/components/SegButtons.vue'

const props = defineProps({
  /** GET /users 의 items (불러오기 전이면 null) */
  rows: { type: Array, default: null },
  total: { type: Number, default: 0 },
  perPage: { type: Number, default: 15 },
  isHq: { type: Boolean, default: false },
  /** 공장 선택 [{ id, name }] */
  factories: { type: Array, default: () => [] },
  factoryLocked: { type: Boolean, default: false },
  selectedId: { type: Number, default: null },
  flashId: { type: Number, default: null },
  meId: { type: Number, default: null },
  query: { type: String, default: '' },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['pick'])
const role = defineModel('role', { type: String, default: '' })
const factoryId = defineModel('factoryId', { type: String, default: '' })
const page = defineModel('page', { type: Number, default: 1 })

const tabs = computed(() => [
  { value: '', label: '전체' },
  ...(props.isHq ? [{ value: ROLE.HQ_ADMIN, label: '본사 관리자' }] : []),
  { value: ROLE.PLANT_ADMIN, label: '공장 담당자' },
  { value: ROLE.WORKER, label: '공정 담당자' },
])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.perPage)))
const from = computed(() => (page.value - 1) * props.perPage + 1)
const to = computed(() => Math.min(props.total, page.value * props.perPage))
</script>

<template>
  <BaseCard title="사용자 목록" :sub="`${total}명`">
    <template #actions>
      <SegButtons v-model="role" :options="tabs" small @update:model-value="page = 1" />
      <select v-model="factoryId" :disabled="factoryLocked" aria-label="공장" @change="page = 1">
        <option v-if="!factoryLocked" value="">전체 공장</option>
        <option v-for="f in factories" :key="f.id" :value="String(f.id)">{{ f.name }}</option>
      </select>
    </template>

    <div class="body" :class="{ busy }">
      <table v-if="rows?.length" class="tb">
        <thead>
          <tr><th>이름</th><th>아이디</th><th>권한</th><th>담당 범위</th><th>최근 접속</th><th class="st">상태</th></tr>
        </thead>
        <TransitionGroup tag="tbody" name="row">
          <tr
            v-for="u in rows"
            :key="u.id"
            class="row"
            :class="{ on: u.id === selectedId, flash: u.id === flashId }"
            tabindex="0"
            @click="emit('pick', u.id)"
            @keydown.enter="emit('pick', u.id)"
          >
            <td>
              <span class="un">
                <span class="av" :class="{ on: u.id === selectedId }">{{ u.name[0] }}</span>
                <b>{{ u.name }}</b><span v-if="u.id === meId" class="m-chip gray">나</span>
              </span>
            </td>
            <td class="mono id">{{ u.loginId }}</td>
            <td><span class="rl"><i :style="{ background: ROLE_COLOR[u.role] }" />{{ ROLE_LABEL[u.role] }}</span></td>
            <td>{{ u.scopeText }}</td>
            <td>
              <span v-if="u.id === meId" class="dotc"><i />접속 중</span>
              <span v-else class="mono time">{{ shortTime(u.lastLoginAt) }}</span>
            </td>
            <td><span class="m-chip" :class="u.active ? 'ok' : 'gray'">{{ u.active ? '사용 중' : '사용 정지' }}</span></td>
          </tr>
        </TransitionGroup>
      </table>
      <div v-else-if="rows" class="empty">
        <AppIcon name="search" :size="24" />
        <b>{{ query ? `'${query}'에 맞는 사용자가 없습니다` : '조건에 맞는 사용자가 없습니다' }}</b>
        이름이나 아이디를 다시 확인해 주세요.
      </div>
      <div v-else class="empty">불러오는 중</div>
    </div>

    <div v-if="rows?.length" class="pg">
      <span class="count">{{ total }}명 중 {{ from }}~{{ to }}</span>
      <button v-for="p in pages" :key="p" type="button" class="mono" :class="{ on: p === page }" @click="page = p">{{ p }}</button>
    </div>
  </BaseCard>
</template>

<style scoped>
select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13px;
  color: var(--color-text);
}

select:disabled {
  background: var(--color-surface-sub);
  color: var(--color-text-sub);
}

.body {
  min-height: 600px;
  transition: opacity 150ms;
}

.body.busy {
  opacity: 0.5;
}

.tb {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}

th {
  height: 40px;
  padding: 0 12px;
  background: var(--color-surface-sub);
  border-bottom: 1px solid var(--color-border);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-sub);
  text-align: left;
  white-space: nowrap;
}

td {
  height: 52px;
  padding: 0 12px;
  border-bottom: 1px solid #f1f5f9;
  white-space: nowrap;
}

th:first-child,
td:first-child {
  padding-left: 20px;
}

.st {
  width: 96px;
}

.row {
  cursor: pointer;
  transition: background var(--m-hover);
}

.row:hover {
  background: var(--color-surface-sub);
}

/* 고른 줄: 연한 파랑 + 왼쪽 파란 막대 */
.row.on td {
  background: var(--color-primary-soft);
}

.row.on td:first-child {
  box-shadow: inset 3px 0 0 var(--color-primary);
}

/* 방금 저장한 줄: 2초 강조 */
.row.flash td {
  animation: flash 2s ease-out;
}

@keyframes flash {
  0%,
  30% {
    background: #dbeafe;
  }
  100% {
    background: transparent;
  }
}

.row-enter-active {
  transition:
    opacity 300ms,
    transform 300ms;
}

.row-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}

.un {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.un b {
  font-weight: 600;
}

.av {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eef2f6;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.av.on {
  background: #bfdbfe;
  color: var(--color-primary-hover);
}

.id,
.time {
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.rl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.rl i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.dotc {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-level-normal-text);
}

.dotc i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-level-normal);
}

.empty {
  height: 600px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.empty b {
  font-size: 14px;
  color: var(--color-text-sub);
}

.pg {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  padding: 0 16px;
  font-size: 12.5px;
  color: var(--color-text-weak);
}

.count {
  margin-right: 8px;
}

.pg button {
  min-width: 26px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
}

.pg button.on {
  background: var(--color-primary-soft);
  font-weight: 600;
  color: var(--color-primary-hover);
}

@media (prefers-reduced-motion: reduce) {
  .row.flash td {
    animation: none;
  }
}
</style>
