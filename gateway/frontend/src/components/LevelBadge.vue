<!--
  상태 배지: 정상 / 주의 / 경보 / 화재 (설계안의 chip 모양: 옅은 배경 + 아이콘 + 글자)
  - 색·이름·아이콘은 types/codes.js 의 LEVEL_META 한 곳에서 가져옴
  - 사용 예: <LevelBadge level="WARN" /> , 문구 바꾸기: <LevelBadge level="ALARM" text="조치 대기" />
-->
<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { LEVEL_META } from '@/types/codes'

const props = defineProps({
  /** NORMAL / WARN / ALARM / FIRE */
  level: { type: String, required: true },
  /** 기본 이름(정상·주의 등) 대신 보여 줄 문구 */
  text: { type: String, default: '' },
})

// 알 수 없는 값이 오면 회색 '-' 로 표시 (화면이 깨지지 않게)
const meta = computed(
  () => LEVEL_META[props.level] ?? { label: '-', icon: 'info', key: 'unknown' },
)
</script>

<template>
  <span class="level-badge" :class="`is-${meta.key}`">
    <AppIcon :name="meta.icon" :size="13" />
    {{ text || meta.label }}
  </span>
</template>

<style scoped>
.level-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
  background: #f1f5f9;
  color: var(--color-text-sub);
}

.level-badge :deep(.app-icon) {
  stroke-width: 2.4;
}

.is-normal {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

.is-warn {
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
}

.is-alarm {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.is-fire {
  background: var(--color-level-fire-soft);
  color: var(--color-level-fire-text);
}
</style>
