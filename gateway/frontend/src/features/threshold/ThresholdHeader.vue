<!--
  기준값 관리 머리 카드 (화면설계안 vThreshold() head())
  - 왼쪽: 설비 아이콘(건조로는 팬, 그 밖은 톱니, 바탕은 설비 상태 색) + 이름 + 상태 배지 + '5공장 · 도장 공정 · 센서 3개 CT-17 · TE-15 · CO-13'
  - 오른쪽: '바꾼 값 n개', 적용 범위(건조로만: 이 설비만 / 건조로 3대 모두), 되돌리기, 저장
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import LevelBadge from '@/components/LevelBadge.vue'
import SegButtons from '@/components/SegButtons.vue'
import { LEVEL } from '@/types/codes'

const props = defineProps({
  /** 트리의 설비 한 줄 { id, name, type, level } */
  equipment: { type: Object, required: true },
  /** 센서 태그 목록 */
  tags: { type: Array, required: true },
  path: { type: String, default: '5공장 · 도장 공정' },
  changeCount: { type: Number, default: 0 },
  canSave: { type: Boolean, default: false },
})

defineEmits(['undo', 'save'])

/** 적용 범위 ('one' 이 설비만 | 'ovens' 건조로 3대) — 건조로만 고를 수 있음 */
const scope = defineModel('scope', { type: String, default: 'one' })
const SCOPES = [
  { value: 'one', label: '이 설비만' },
  { value: 'ovens', label: '건조로 3대 모두' },
]

const oven = computed(() => props.equipment.type === 'OVEN')
const tone = computed(() => (props.equipment.level === LEVEL.NORMAL ? 'normal' : props.equipment.level === LEVEL.WARN ? 'warn' : 'alarm'))
</script>

<template>
  <BaseCard class="head-card">
    <div class="row">
      <span class="ico" :class="tone"><AppIcon :name="oven ? 'fan' : 'gear'" :size="26" /></span>
      <div class="info">
        <div class="name">
          <b>{{ equipment.name }}</b>
          <LevelBadge :level="equipment.level" />
        </div>
        <div class="path">
          {{ path }} · {{ tags.length ? `센서 ${tags.length}개` : '연결된 센서 없음' }}
          <span class="mono">{{ tags.join(' · ') }}</span>
        </div>
      </div>

      <div class="right">
        <span v-if="changeCount" :key="changeCount" class="count">바꾼 값 {{ changeCount }}개</span>
        <template v-if="oven && tags.length">
          <span class="lb">적용 범위</span>
          <SegButtons v-model="scope" :options="SCOPES" />
        </template>
        <button type="button" class="btn" :disabled="!changeCount" @click="$emit('undo')"><AppIcon name="undo" :size="16" />되돌리기</button>
        <button type="button" class="btn btn-primary save" :disabled="!canSave" @click="$emit('save')"><AppIcon name="save" :size="16" />저장</button>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.row {
  height: 84px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 20px;
}

/* 설비 아이콘 상자: 상태 색 바탕 */
.ico {
  width: 48px;
  height: 48px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
}

.ico.normal {
  background: var(--color-level-normal-soft);
  color: var(--color-level-normal-text);
}

.ico.warn {
  background: var(--color-level-warn-soft);
  color: var(--color-level-warn-text);
}

.ico.alarm {
  background: var(--color-level-alarm-soft);
  color: var(--color-level-alarm-text);
}

.info {
  min-width: 0;
}

.name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name b {
  font-size: 20px;
  letter-spacing: -0.3px;
}

.path {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-sub);
  white-space: nowrap;
}

.path .mono {
  margin-left: 4px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

/* 바꾼 값 수: 바뀔 때마다 살짝 나타남 */
.count {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-level-warn-text);
  animation: softin 200ms ease-out both;
}

.lb {
  font-size: 13px;
  color: var(--color-text-sub);
}

.right .btn {
  height: 40px;
}

.save {
  padding: 0 20px;
}
</style>
