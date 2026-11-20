<!--
  카드 제목 자리의 단계 경로 (2026-11-18 재원님 요청)
  - 메인 대시보드 4단계를 같은 이름 규칙으로: 사업장 › 울산공장 › 5공장 › 도장 공정
  - 맨 앞 '사업장'은 항상 붙임: 본사 관리자는 누르면 사업장 선택 화면으로, 다른 권한은 글자만
    (사업장 선택 화면은 본사 관리자만 들어갈 수 있음)
  - 앞 단계는 누르면 그 화면으로 이동(to 가 있을 때), 마지막(지금 화면)은 굵게
  - 사용 예 (BaseCard 의 title 슬롯):
    <BaseCard><template #title><CardCrumb :items="[{ label: '울산공장', to }, { label: '5공장' }]" /></template> ... </BaseCard>
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/types/codes'

const props = defineProps({
  /** '사업장' 다음 단계들 [{ label, to }] — to 가 있으면 링크, 없으면 글자만 */
  items: { type: Array, default: () => [] },
})

const auth = useAuthStore()

// 맨 앞 '사업장' + 넘겨받은 단계들
const all = computed(() => [
  { label: '사업장', to: auth.role === ROLE.HQ_ADMIN ? { name: 'site-select' } : null },
  ...props.items,
])
</script>

<template>
  <span class="card-crumb">
    <template v-for="(c, i) in all" :key="i">
      <AppIcon v-if="i > 0" name="cr" :size="15" class="sep" />
      <b v-if="i === all.length - 1" class="now">{{ c.label }}</b>
      <RouterLink v-else-if="c.to" :to="c.to" class="link">{{ c.label }}</RouterLink>
      <span v-else class="plain">{{ c.label }}</span>
    </template>
  </span>
</template>

<style scoped>
/* 글자 크기는 카드 제목(h3)과 같게: 지금 화면만 굵게, 앞 단계는 보통 굵기 */
.card-crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  letter-spacing: -0.2px;
}

.sep {
  color: var(--color-text-weak);
}

.now {
  font-weight: 700;
  color: var(--color-text);
}

.plain {
  font-weight: 500;
  color: var(--color-text-sub);
}

.link {
  font-weight: 500;
  color: var(--color-primary);
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
