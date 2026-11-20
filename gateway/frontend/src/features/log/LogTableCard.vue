<!--
  이용 로그 표 (화면설계안 vLogs() #lc)
  - 열: 시간, 아이디, 이름, 담당, 유형(색 칩), 내용 — 최근 순, 한 쪽 16행 (설계안)
  - 아래: 'n건 중 a~b' + 쪽 번호 (5쪽 넘으면 … 로 줄임)
  - 쪽을 넘기거나 다시 불러오면 표가 0.15초에 흐려졌다 나타남
  - 결과가 없으면 '조건에 맞는 기록이 없습니다'
-->
<script setup>
import { computed } from 'vue'
import { groupOfType } from './logMeta'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'

const props = defineProps({
  /** GET /logs items (없으면 null = 처음 불러오는 중) */
  rows: { type: Array, default: null },
  total: { type: Number, default: 0 },
  perPage: { type: Number, required: true },
  busy: { type: Boolean, default: false },
  /** 다시 불러올 때마다 바뀌는 값 (표 나타남 효과용) */
  loadKey: { type: Number, default: 0 },
})

const page = defineModel('page', { type: Number, required: true })

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.perPage)))
/** 쪽 번호 (설계안: 처음 · 끝 · 지금 ±2, 나머지는 …) */
const pageList = computed(() => {
  const out = []
  for (let i = 1; i <= pages.value; i++) {
    if (i === 1 || i === pages.value || Math.abs(i - page.value) <= 2) out.push(i)
    else if (out.at(-1) !== '…') out.push('…')
  }
  return out
})
const from = computed(() => (page.value - 1) * props.perPage + 1)
const to = computed(() => Math.min(props.total, page.value * props.perPage))

/** '2026-11-17T08:41:05+09:00' → '2026-11-17 08:41:05' */
const timeOf = (at) => `${at.slice(0, 10)} ${at.slice(11, 19)}`
</script>

<template>
  <BaseCard title="이용 로그" :sub="`${total}건`">
    <div class="body" :class="{ busy }">
      <table v-if="rows?.length" class="tb">
        <thead>
          <tr>
            <th class="t-time">시간</th>
            <th class="t-id">아이디</th>
            <th class="t-name">이름</th>
            <th class="t-scope">담당</th>
            <th class="t-type">유형</th>
            <th>내용</th>
          </tr>
        </thead>
        <tbody :key="`${loadKey}-${page}`" class="fade">
          <tr v-for="r in rows" :key="r.id">
            <td class="mono t-time">{{ timeOf(r.at) }}</td>
            <td class="mono t-id">{{ r.loginId }}</td>
            <td class="t-name"><b>{{ r.name }}</b></td>
            <td class="t-scope">{{ r.scopeText }}</td>
            <td class="t-type">
              <span class="m-chip" :class="groupOfType(r.type)?.chip ?? 'gray'">{{ groupOfType(r.type)?.label ?? r.type }}</span>
            </td>
            <td class="msg" :title="r.message">{{ r.message }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="rows" class="noval">
        <AppIcon name="search" :size="24" />
        <b>조건에 맞는 기록이 없습니다</b>
        기간이나 유형을 바꿔 다시 조회해 주세요.
      </div>
      <div v-else class="noval">불러오는 중</div>
    </div>

    <div v-if="rows?.length" class="pg">
      <span class="count">{{ total }}건 중 {{ from }}~{{ to }}</span>
      <template v-for="(p, i) in pageList" :key="`${p}-${i}`">
        <span v-if="p === '…'" class="dots">…</span>
        <button v-else type="button" class="mono" :class="{ on: p === page }" :aria-current="p === page ? 'page' : undefined" @click="page = p">
          {{ p }}
        </button>
      </template>
    </div>
  </BaseCard>
</template>

<style scoped>
.body {
  min-height: 720px; /* 16행 + 머리줄 — 쪽마다 높이가 흔들리지 않게 */
  transition: opacity 150ms;
}

.body.busy {
  opacity: 0.4;
}

.tb {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
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
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

th:first-child,
td:first-child {
  padding-left: 20px;
}

.t-time {
  width: 190px;
  font-size: 12.5px;
}

.t-id {
  width: 150px;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.t-name {
  width: 110px;
}

.t-scope {
  width: 200px;
  color: var(--color-text-sub);
}

.t-type {
  width: 130px;
}

b {
  font-weight: 600;
}

/* 설계안 info(하늘색) 칩 — alert.css 에 없는 색 */
.m-chip.sky {
  background: #e0f2fe;
  color: #0369a1;
}

/* 쪽 넘김 · 다시 불러오기: 0.15초 (화면설계서 G2) */
.fade {
  animation: softin 150ms ease-out both; /* base.css */
}

.noval {
  height: 520px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.noval b {
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

.pg .count {
  margin-right: 8px;
}

.pg button,
.pg .dots {
  min-width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--color-primary);
}

.pg button {
  cursor: pointer;
}

.pg button.on {
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-weight: 600;
}

.pg .dots {
  color: var(--color-text-weak);
}

@media (prefers-reduced-motion: reduce) {
  .fade {
    animation: none;
  }
}
</style>
