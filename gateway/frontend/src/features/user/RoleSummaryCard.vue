<!--
  권한별 사용자 (화면설계안 vUsers() summary())
  - 도넛(전체 인원) + 권한별 인원 + 사용 정지 인원
  - 권한 줄을 누르면 사용자 목록이 그 권한으로 걸러짐 (화면설계서 G1 ①)
  - 아래 한 줄: 본사 관리자는 모든 사용자를 관리 / 공장 담당자는 소속 공장 · 공정 사용자만
-->
<script setup>
import { computed } from 'vue'
import { ROLE, ROLE_COLOR, ROLE_LABEL } from './userMeta'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import DonutRing from '@/components/DonutRing.vue'

const props = defineProps({
  /** GET /users 의 counts { HQ_ADMIN, PLANT_ADMIN, WORKER } */
  counts: { type: Object, default: null },
  inactiveCount: { type: Number, default: 0 },
  isHq: { type: Boolean, default: false },
  sub: { type: String, default: '' },
})

defineEmits(['pick'])

const roles = computed(() => (props.isHq ? [ROLE.HQ_ADMIN, ROLE.PLANT_ADMIN, ROLE.WORKER] : [ROLE.PLANT_ADMIN, ROLE.WORKER]))
const total = computed(() => roles.value.reduce((s, r) => s + (props.counts?.[r] ?? 0), 0))
const parts = computed(() => roles.value.map((r) => ({ value: props.counts?.[r] ?? 0, color: ROLE_COLOR[r] })))
</script>

<template>
  <BaseCard title="권한별 사용자" :sub="sub">
    <div class="body">
      <DonutRing :parts="parts" :size="132" :stroke="16">
        <b class="mono big">{{ counts ? total : '–' }}</b><small>명</small>
      </DonutRing>
      <div class="legend">
        <button v-for="r in roles" :key="r" type="button" class="lr" @click="$emit('pick', r)">
          <i :style="{ background: ROLE_COLOR[r] }" />{{ ROLE_LABEL[r] }}<b class="mono">{{ counts?.[r] ?? '–' }}<small>명</small></b>
        </button>
        <div class="lr static"><i class="stop" />사용 정지<b class="mono">{{ inactiveCount }}<small>명</small></b></div>
      </div>
    </div>
    <p class="foot"><AppIcon name="info" :size="15" />{{ isHq ? '본사 관리자는 모든 사용자를 관리' : '소속 공장 · 공정 사용자만 관리' }}</p>
  </BaseCard>
</template>

<style scoped>
.body {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 20px 20px 24px;
}

.big {
  font-size: 26px;
  font-weight: 600;
}

small {
  margin-left: 2px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.legend {
  flex: 1;
  min-width: 0;
}

.lr {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  border: none;
  border-top: 1px solid #f1f5f9;
  background: none;
  font-size: 13.5px;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: background var(--m-hover);
}

.lr:first-child {
  border-top: none;
}

.lr:not(.static):hover {
  background: var(--color-surface-sub);
}

.lr.static {
  cursor: default;
}

.lr i {
  width: 10px;
  height: 10px;
  flex: none;
  border-radius: 3px;
}

.lr i.stop {
  background: #cbd5e1;
}

.lr b {
  margin-left: auto;
  font-size: 15px;
}

.foot {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 20px;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-sub);
}

.foot svg {
  color: var(--color-text-weak);
}
</style>
