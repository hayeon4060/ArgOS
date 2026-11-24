<!--
  설비 상세 제목 줄 (설계안 vEquip() 의 .topbar, 카드 밖 한 줄)
  - 왼쪽: 경로 '울산공장 › 5공장 › 도장 공정 › 상도 건조로' + 상태 배지(크게) + 체크리스트 진행 배지(누르면 진행 현황 창)
  - 오른쪽: '설비 배치도' 돌아가기 버튼 ('설비 기준값 관리' 버튼은 2026-11-22 재원님 요청으로 삭제 — 기준값은 공장 관리에서)
    (추이 범위 1시간 / 6시간 / 오늘 은 2026-11-19 센서 추이 카드 제목줄로 옮김 → TrendCard)
-->
<script setup>
import { computed } from 'vue'
import ChecklistBadge from './ChecklistBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import { useAlertStore } from '@/stores/alert'

const alert = useAlertStore()
// 경보·화재 중에는 체크리스트 배지를 숨김 (상단 바와 같은 규칙)
const emergency = computed(() => ['ALARM', 'FIRE'].includes(alert.primary?.level))

defineProps({
  /** 경로: [{ label, to }] — to 가 있으면 링크 */
  crumbs: { type: Array, required: true },
  level: { type: String, required: true },
  /** 진행 중 체크리스트 (없으면 null) */
  checklist: { type: Object, default: null },
  /** 돌아갈 공정 배치도 라우트 */
  backTo: { type: Object, required: true },
})
</script>

<template>
  <div class="topbar">
    <span class="crumb">
      <template v-for="(c, i) in crumbs" :key="c.label">
        <AppIcon v-if="i > 0" name="cr" :size="14" />
        <RouterLink v-if="c.to" :to="c.to" class="link">{{ c.label }}</RouterLink>
        <b v-else>{{ c.label }}</b>
      </template>
    </span>
    <LevelBadge :level="level" class="big" />

    <div class="right">
      <ChecklistBadge v-if="checklist && !emergency" :checklist="checklist" @click="alert.open('checklist', { checklistId: checklist.id })" />
      <RouterLink :to="backTo" class="btn back"><AppIcon name="cl" :size="16" />설비 배치도</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 40px;
  margin-bottom: 10px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 16px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.crumb .app-icon {
  color: var(--color-text-weak);
}

.crumb b {
  color: var(--color-text);
}

.link {
  color: var(--color-primary);
  font-weight: 500;
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 설계안 .chip.lgc: 제목 줄의 큰 상태 배지 */
.big {
  padding: 4px 12px;
  font-size: 14px;
  line-height: 20px;
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn {
  height: 36px;
}

.back {
  text-decoration: none;
}
</style>
