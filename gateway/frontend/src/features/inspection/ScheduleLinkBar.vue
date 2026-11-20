<!--
  수기 서류 등록 창의 '점검 건 연결' 줄 (인식이 끝난 뒤 원본 대조 단계에서 보임)
  - 문자 인식으로 읽은 양식 · 설비 · 날짜로 맞는 예정 · 누락 건을 미리 골라 둠(추천) → 사용자가 확인하거나 바꿈
  - 목록: 오늘 포함 최근 7일의 예정 · 누락 건 + '연결 안 함 (새 이력으로 저장)'
  - 고른 건은 '검수 완료' 저장 때 함께 보냄 → 그 건이 점검 이력에서 '완료 · 수기'로 바뀜
  - 저장한 뒤에는 이은 결과만 글자로 보여 줌 (linked)
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { SCHEDULE_STATE } from './inspectionMeta'

const props = defineProps({
  /** 예정 · 누락 건 (getSchedule 한 줄 모양) */
  candidates: { type: Array, required: true },
  /** 추천한 건 번호 (없으면 null) */
  suggested: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
  /** 저장 뒤 이은 결과 문구 (있으면 고르는 칸 대신 보여 줌) */
  linked: { type: String, default: '' },
})

/** 고른 건 번호 (null = 연결 안 함) */
const model = defineModel({ type: Number, default: null })

/** 줄 이름: '11-20 09:20 · 도료 혼합실 · 박성훈 · 예정' */
const labelOf = (c) => `${c.date.slice(5)} ${c.time} · ${c.equipmentName} · ${c.assignee} · ${SCHEDULE_STATE[c.status].label}`
const hint = computed(() => {
  if (props.suggested && model.value === props.suggested) return '인식한 양식 · 설비 · 날짜로 맞는 점검 건을 골라 두었습니다. 다르면 바꿔 주세요.'
  if (props.suggested) return '추천과 다른 건을 골랐습니다.'
  return '맞는 예정 · 누락 건을 찾지 못했습니다. 목록에서 고르거나, 연결 없이 새 이력으로 저장하세요.'
})
</script>

<template>
  <div class="link-bar" :class="{ ok: linked }">
    <AppIcon name="cal" :size="16" />
    <b>점검 건 연결</b>
    <template v-if="linked">
      <span class="done">{{ linked }}</span>
    </template>
    <template v-else>
      <select v-model="model" :disabled="disabled" aria-label="이을 점검 건">
        <option :value="null">연결 안 함 (새 이력으로 저장)</option>
        <option v-for="c in candidates" :key="c.scheduleId" :value="c.scheduleId">
          {{ labelOf(c) }}{{ c.scheduleId === suggested ? '  ★ 추천' : '' }}
        </option>
      </select>
      <span class="hint" :class="{ rec: suggested && model === suggested }">{{ hint }}</span>
    </template>
  </div>
</template>

<style scoped>
.link-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-primary-soft);
  font-size: 13px;
  color: var(--color-primary-hover);
  white-space: nowrap;
  animation: softin 220ms ease-out both; /* base.css — 인식이 끝나면 부드럽게 나타남 */
}

.link-bar.ok {
  background: #f0fdf4;
  color: var(--color-level-normal-text);
}

b {
  font-weight: 700;
}

select {
  height: 32px;
  min-width: 360px;
  padding: 0 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13px;
  color: var(--color-text);
}

.hint {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12.5px;
  color: var(--color-text-sub);
}

.hint.rec {
  color: var(--color-primary-hover);
}

.done {
  font-weight: 600;
}
</style>
