<!--
  사업장 목록 카드 (사업장 → 공장 → 공정 → 센서)
  - 제목줄 오른쪽: 전체 / 주의 이상 필터
  - 처음부터 모든 사업장을 펼쳐 두고 공장 목록을 불러옴 (접었다 다시 펼칠 때는 저장된 값 사용, useSiteTrees)
  - 이벤트: enter(사업장 ID), enter-factory(공장), add(사업장 추가 단추)
  - 2026-11-20 재원님 요청: 제목줄 오른쪽에 '사업장 추가' 단추 (이 화면은 본사 관리자 전용이라 누구나 보임)
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import SiteListItem from './SiteListItem.vue'
import { useSiteTrees } from './useSiteTrees'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  sites: { type: Array, required: true },
  /** 강조할 사업장 ID (파란 테두리 · '이동' 버튼 강조) */
  currentSiteId: { type: Number, default: null },
  /** 누를 수 있는 공장 ID */
  linkFactoryId: { type: Number, default: null },
})

const emit = defineEmits(['enter', 'enter-factory', 'add'])

const { trees, loadTree } = useSiteTrees()

// 펼친 사업장: 화면을 처음 열 때 모든 사업장을 펼쳐 둠 (2026-11-20 재원님 요청, 전에는 강조 사업장만)
const openIds = ref(new Set(props.sites.map((s) => s.id)))
onMounted(() => openIds.value.forEach(loadTree))

function toggle(siteId) {
  const next = new Set(openIds.value)
  if (next.has(siteId)) {
    next.delete(siteId)
  } else {
    next.add(siteId)
    loadTree(siteId)
  }
  openIds.value = next
}

// 필터: 전체 / 주의 이상(정상이 아닌 사업장만)
const onlyAbnormal = ref(false)
const visibleSites = computed(() =>
  onlyAbnormal.value ? props.sites.filter((s) => s.level !== LEVEL.NORMAL) : props.sites,
)
</script>

<template>
  <BaseCard title="사업장 목록" :sub="`${sites.length}곳 · 사업장 → 공장 → 공정 → 센서`">
    <template #actions>
      <button type="button" class="btn add" @click="emit('add')"><AppIcon name="plus" :size="16" />사업장 추가</button>
      <span class="seg">
        <button type="button" :class="{ on: !onlyAbnormal }" @click="onlyAbnormal = false">전체</button>
        <button type="button" :class="{ on: onlyAbnormal }" @click="onlyAbnormal = true">주의 이상</button>
      </span>
    </template>

    <div class="list">
      <div v-if="visibleSites.length === 0" class="empty">
        <AppIcon name="check" :size="28" />
        <b>주의 이상인 사업장이 없습니다</b>
        모든 사업장이 정상입니다.
      </div>

      <SiteListItem
        v-for="site in visibleSites"
        :key="site.id"
        :site="site"
        :open="openIds.has(site.id)"
        :current="site.id === currentSiteId"
        :tree="trees[site.id]"
        :link-factory-id="linkFactoryId"
        @toggle="toggle"
        @enter="emit('enter', $event)"
        @enter-factory="emit('enter-factory', $event)"
      />
    </div>
  </BaseCard>
</template>

<style scoped>
.list {
  padding: 16px 20px;
}

.add {
  height: 32px;
  margin-right: 8px;
  padding: 0 12px;
  font-size: 13px;
}

/* 구분 버튼 (전체 / 주의 이상) */
.seg {
  display: inline-flex;
  padding: 3px;
  border-radius: var(--radius-sm);
  background: #f1f5f9;
}

.seg button {
  padding: 5px 12px;
  border: none;
  border-radius: var(--radius-xs);
  background: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-sub);
  cursor: pointer;
}

.seg button.on {
  background: #fff;
  color: var(--color-text);
  font-weight: 700;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.12);
}

.empty {
  height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-sub);
}

.empty .app-icon {
  color: var(--color-level-normal);
}

.empty b {
  font-size: 15px;
  color: var(--color-text);
}
</style>
